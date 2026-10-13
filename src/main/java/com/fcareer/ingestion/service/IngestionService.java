package com.fcareer.ingestion.service;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.alias.ClubDataQuality;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.LeagueEntity;
import com.fcareer.catalog.entity.NationalityEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.GameVersionRepository;
import com.fcareer.catalog.repository.LeagueRepository;
import com.fcareer.catalog.repository.NationalityRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.mapper.AcceleRateDeriver;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.model.IngestionResult;
import com.fcareer.ingestion.provider.ExternalDataProvider;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final String GAME_VERSION = "game_version";

    private final ExternalDataProvider provider;
    private final IngestionProperties properties;
    private final GameVersionRepository gameVersionRepository;
    private final BasePlayerRepository basePlayerRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final NationalityRepository nationalityRepository;
    private final LeagueRepository leagueRepository;
    private final ClubRepository clubRepository;
    private final com.fcareer.catalog.alias.ClubAliasRepository clubAliasRepository;
    private final RatingAttrAccessor ratingAttrAccessor;
    private final AcceleRateDeriver acceleRateDeriver;
    private final TransactionTemplate transactionTemplate;
    private final IngestionRunRepository runs;
    private final JdbcTemplate jdbc;
    private final com.fcareer.catalog.service.LeagueTierService leagueTierService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ingestion");
        thread.setDaemon(true);
        return thread;
    });

    /** Başlatılan koşu: runId yeni ya da devam eden koşunun kimliği; alreadyRunning true ise başka bir koşu sürüyor. */
    public record Started(Long runId, boolean alreadyRunning) {
    }

    private record Claim(long runId, Optional<IngestionRunRepository.Run> resumed) {
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Koşuyu bu thread'de çalıştırır (başlangıç koşucusu ve testler). */
    public IngestionResult run() {
        return claim().map(this::ingest).orElseGet(() -> {
            log.warn("Ingestion already running provider : {}", provider.name());
            return IngestionResult.alreadyRunning(provider.name());
        });
    }

    /** Koşuyu DB'de sahiplenip arka planda çalıştırır; HTTP isteği dakikalarca beklemez (BE-BUG-16). */
    public Started startAsync() {
        return claim().map(claim -> {
            executor.submit(() -> {
                try {
                    ingest(claim);
                } catch (RuntimeException e) {
                    log.error("Background ingestion failed runId : {}", claim.runId(), e);
                }
            });
            return new Started(claim.runId(), false);
        }).orElseGet(() -> new Started(null, true));
    }

    /** Bayat RUNNING koşuları FAILED yapar, ardından son FAILED koşuyu sürdürür ya da yenisini açar; başka bir canlı koşu varsa boş döner. */
    private Optional<Claim> claim() {
        runs.expireStale(provider.name(), properties.getStaleAfter());
        Optional<IngestionRunRepository.Run> resumable = runs.findResumable(provider.name());
        if (resumable.isPresent()) {
            return runs.resume(resumable.get().id()) ? Optional.of(new Claim(resumable.get().id(), resumable)) : Optional.empty();
        }
        return runs.start(provider.name()).map(id -> new Claim(id, Optional.empty()));
    }

    private IngestionResult ingest(Claim claim) {
        short gameVersion = properties.getGameVersionId();
        long runId = claim.runId();
        if (!gameVersionRepository.existsById(gameVersion)) {
            runs.fail(runId, "unknown game version " + gameVersion);
            throw new NotFoundException(GAME_VERSION, gameVersion);
        }
        int pageSize = properties.getPageSize();
        int limit = properties.getMaxPlayers();
        claim.resumed().ifPresent(run -> log.info("Ingestion resuming runId : {} offset : {}", run.id(), run.nextOffset()));
        int created = claim.resumed().map(IngestionRunRepository.Run::created).orElse(0);
        int updated = claim.resumed().map(IngestionRunRepository.Run::updated).orElse(0);
        int total = Integer.MAX_VALUE;
        int offset = claim.resumed().map(IngestionRunRepository.Run::nextOffset).orElse(0);
        try {
            while (offset < total) {
                if (limit > 0 && offset >= limit) {
                    break;
                }
                ExternalPlayerPage page = fetchWithRetry(offset, pageSize);
                if (page.totalItems() <= 0) {
                    throw new IllegalStateException("provider returned no total item count at offset " + offset);
                }
                total = page.totalItems();
                int pageOffset = offset;
                List<ExternalPlayer> items = limit > 0 ? page.items().stream().limit(limit - pageOffset).toList() : page.items();
                int[] counts = transactionTemplate.execute(status -> upsertPage(items, gameVersion, runId));
                created += counts[0];
                updated += counts[1];
                offset = Math.min(offset + pageSize, total);
                runs.progress(runId, offset, total, created, updated);
                log.info("Ingestion page done provider : {} offset : {} total : {}", provider.name(), pageOffset, total);
                if (page.items().isEmpty() && total > offset) {
                    log.warn("Ingestion page returned no usable items offset : {}", offset - pageSize);
                }
                pause();
            }
            if (limit == 0) {
                retireUnseen(runId);
            }
        } catch (RuntimeException e) {
            runs.fail(runId, e.getMessage());
            log.error("Ingestion failed runId : {} (will resume from last offset)", runId, e);
            throw e;
        }
        runs.finish(runId);
        leagueTierService.apply();
        log.info("Ingestion finished provider : {} created : {} updated : {}", provider.name(), created, updated);
        return new IngestionResult(provider.name(), gameVersion, created, updated, false);
    }

    /**
     * Tam koşuda bu koşuda görülmeyen (daha önce siteden gelmiş) oyuncuları emekliye ayırır; kariyer oyuncuları ve LE'den gelen
     * (hiç görülmemiş) oyuncular etkilenmez. Site bir anda çok küçük dönerse (görülen < önceki görülenin oranı) toplu emeklilik yapılmaz.
     */
    private void retireUnseen(long runId) {
        Integer seen = jdbc.queryForObject("SELECT count(*) FROM base_player WHERE last_seen_run = ?", Integer.class, runId);
        Integer before = jdbc.queryForObject("SELECT count(*) FROM base_player WHERE last_seen_run IS NOT NULL AND last_seen_run <> ? AND retired_at IS NULL", Integer.class, runId);
        if (Objects.isNull(seen) || Objects.isNull(before) || before == 0) {
            return;
        }
        if (seen < before * properties.getRetireMinRatio()) {
            log.warn("Ingestion retirement skipped, catalog suspiciously small runId : {} seen : {} previouslySeen : {}", runId, seen, before);
            return;
        }
        int retired = jdbc.update("UPDATE base_player SET retired_at = now() WHERE owner_career_id IS NULL AND last_seen_run IS NOT NULL AND last_seen_run <> ? AND retired_at IS NULL", runId);
        log.info("Ingestion retired unseen players runId : {} retired : {}", runId, retired);
    }

    /** Geçici hatada üstel geri çekilmeyle yeniden dener (BUG-09); üst sınır aşılırsa koşu kaldığı offset'te FAILED kalır. */
    private ExternalPlayerPage fetchWithRetry(int offset, int pageSize) {
        int attempts = Math.max(1, properties.getRetryAttempts());
        long backoff = properties.getRetryBackoffMillis();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return provider.fetch(offset, pageSize);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Ingestion page failed offset : {} attempt : {}/{} reason : {}", offset, attempt, attempts, e.getMessage());
                if (attempt < attempts && backoff > 0) {
                    try {
                        Thread.sleep(backoff << (attempt - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    private int[] upsertPage(List<ExternalPlayer> items, short gameVersion, long runId) {
        Map<Long, BasePlayerEntity> existing = basePlayerRepository
                .findByEaResourceIdIn(items.stream().map(ExternalPlayer::externalId).toList()).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getEaResourceId, Function.identity()));
        Map<Long, PlayerRatingEntity> ratings = playerRatingRepository
                .findByBasePlayerIdInAndGameVersionId(existing.values().stream().map(BasePlayerEntity::getId).toList(), gameVersion)
                .stream().collect(Collectors.toMap(PlayerRatingEntity::getBasePlayerId, Function.identity()));
        Map<String, NationalityEntity> nationalities = new HashMap<>();
        Map<String, LeagueEntity> leagues = new HashMap<>();
        Map<Long, Integer> clubIds = new HashMap<>();
        int created = 0;
        int updated = 0;
        for (ExternalPlayer player : items) {
            BasePlayerEntity base = existing.get(player.externalId());
            boolean isNew = Objects.isNull(base);
            if (isNew) {
                base = new BasePlayerEntity();
                base.setEaResourceId(player.externalId());
            }
            applyBase(base, player, nationalities);
            base.setLastSeenRun(runId);
            base.setRetiredAt(null);
            base = basePlayerRepository.save(base);
            PlayerRatingEntity rating = ratings.getOrDefault(base.getId(), new PlayerRatingEntity());
            rating.setBasePlayerId(base.getId());
            rating.setGameVersionId(gameVersion);
            applyRating(rating, player, base, leagues, clubIds);
            playerRatingRepository.save(rating);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return new int[]{created, updated};
    }

    private void applyBase(BasePlayerEntity base, ExternalPlayer player, Map<String, NationalityEntity> cache) {
        base.setFullName(player.fullName());
        base.setDob(player.dob());
        // FC27 sayfası boy/kiloyu boş döndürüyor; daha önce dökümden doldurulmuş değerler ezilmez.
        if (Objects.nonNull(player.heightCm())) {
            base.setHeightCm(toShort(player.heightCm()));
        }
        if (Objects.nonNull(player.weightKg())) {
            base.setWeightKg(toShort(player.weightKg()));
        }
        base.setPreferredFoot(player.preferredFoot());
        base.setGender((short) player.gender());
        base.setNationalityId(Objects.isNull(player.nationality()) ? null : cache
                .computeIfAbsent(player.nationality(), this::nationality).getId());
    }

    private void applyRating(PlayerRatingEntity rating, ExternalPlayer player, BasePlayerEntity base,
                             Map<String, LeagueEntity> leagues, Map<Long, Integer> clubIds) {
        rating.setOverall((short) player.overall());
        rating.setPositions(player.positions());
        rating.setWeakFoot(toShort(player.weakFoot()));
        rating.setSkillMoves(toShort(player.skillMoves()));
        rating.setFaceStats(Objects.isNull(player.faceStats()) ? null : player.faceStats().stream().map(Integer::shortValue).toList());
        rating.setPlaystyles(player.playstyles());
        rating.setAccelerate(acceleRateDeriver.derive(Objects.isNull(base.getHeightCm()) ? null : base.getHeightCm().intValue(),
                player.gender(), player.attrs()).map(AcceleRate::name).orElse(null));
        ratingAttrAccessor.write(rating, player.attrs());
        rating.setClubId(club(player, leagues, clubIds));
    }

    /** Kulüp EA team.id ile tanınır (küme düşünce lig değişse de aynı satır güncellenir); id yoksa ad + lig. Sayfa başına önbellek N+1'i önler. */
    private Integer club(ExternalPlayer player, Map<String, LeagueEntity> leagues, Map<Long, Integer> clubIds) {
        if (Objects.isNull(player.clubName()) || ClubDataQuality.isPlaceholder(player.clubName()) || player.clubName().contains("\uFFFD")) {
            return null; // yer tutucu ("*TeamName…") ve bozuk (U+FFFD) kulüp adları katalogda kulüp olmaz
        }
        Long teamId = player.clubExternalId();
        if (Objects.nonNull(teamId) && clubIds.containsKey(teamId)) {
            return clubIds.get(teamId);
        }
        Integer leagueId = Objects.isNull(player.leagueName()) ? null
                : leagues.computeIfAbsent(player.leagueName(), this::league).getId();
        Optional<ClubEntity> known = Objects.isNull(teamId) ? Optional.empty() : clubRepository.findFirstByEaTeamIdOrderByIdAsc(teamId.intValue());
        ClubEntity club = known.or(() -> clubAliasRepository.findClubId(player.clubName(), leagueId, player.gender()).flatMap(clubRepository::findById))
                .or(() -> clubRepository.findByNameAndLeagueId(player.clubName(), leagueId)).orElseGet(ClubEntity::new);
        // Gelen ad tanınan kulübün alias'ıysa kanonik ad korunur; değilse (ad, lig) güncellenir.
        boolean aliasOfThisClub = Objects.nonNull(club.getId()) && clubAliasRepository.findClubId(player.clubName(), leagueId, player.gender()).filter(club.getId()::equals).isPresent();
        if (!aliasOfThisClub) {
            club.setName(player.clubName());
            club.setLeagueId(leagueId);
        }
        club.setGender((short) player.gender());
        if (Objects.nonNull(teamId)) {
            club.setEaTeamId(teamId.intValue());
        }
        Integer id = clubRepository.save(club).getId();
        if (Objects.nonNull(teamId)) {
            clubIds.put(teamId, id);
        }
        return id;
    }

    private NationalityEntity nationality(String name) {
        return nationalityRepository.findByName(name).orElseGet(() -> {
            NationalityEntity entity = new NationalityEntity();
            entity.setName(name);
            return nationalityRepository.save(entity);
        });
    }

    private LeagueEntity league(String name) {
        return leagueRepository.findByName(name).orElseGet(() -> {
            LeagueEntity entity = new LeagueEntity();
            entity.setName(name);
            return leagueRepository.save(entity);
        });
    }

    private void pause() {
        long delay = provider.pageDelayMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Short toShort(Integer value) {
        return Objects.isNull(value) ? null : value.shortValue();
    }
}
