package com.fcareer.ingestion.service;

import com.fcareer.catalog.entity.BasePlayerEntity;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final RatingAttrAccessor ratingAttrAccessor;
    private final AcceleRateDeriver acceleRateDeriver;
    private final TransactionTemplate transactionTemplate;
    private final IngestionRunRepository runs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IngestionResult run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Ingestion already running provider : {}", provider.name());
            return IngestionResult.alreadyRunning(provider.name());
        }
        try {
            return ingest();
        } finally {
            running.set(false);
        }
    }

    private IngestionResult ingest() {
        short gameVersion = properties.getGameVersionId();
        if (!gameVersionRepository.existsById(gameVersion)) {
            throw new NotFoundException(GAME_VERSION, gameVersion);
        }
        int pageSize = properties.getPageSize();
        int limit = properties.getMaxPlayers();
        var resumable = runs.findResumable(provider.name());
        long runId = resumable.map(IngestionRunRepository.Run::id).orElseGet(() -> runs.start(provider.name()));
        resumable.ifPresent(run -> {
            runs.resume(run.id());
            log.info("Ingestion resuming runId : {} offset : {}", run.id(), run.nextOffset());
        });
        int created = resumable.map(IngestionRunRepository.Run::created).orElse(0);
        int updated = resumable.map(IngestionRunRepository.Run::updated).orElse(0);
        int total = Integer.MAX_VALUE;
        try {
            for (int offset = resumable.map(IngestionRunRepository.Run::nextOffset).orElse(0); offset < total; offset += pageSize) {
                if (limit > 0 && offset >= limit) {
                    break;
                }
                ExternalPlayerPage page = fetchWithRetry(offset, pageSize);
                total = page.totalItems();
                int pageOffset = offset;
                List<ExternalPlayer> items = limit > 0 ? page.items().stream().limit(limit - pageOffset).toList() : page.items();
                int[] counts = transactionTemplate.execute(status -> upsertPage(items, gameVersion));
                created += counts[0];
                updated += counts[1];
                runs.progress(runId, offset + pageSize, total, created, updated);
                log.info("Ingestion page done provider : {} offset : {} total : {}", provider.name(), offset, total);
                if (page.items().isEmpty() && total > offset + pageSize) {
                    log.warn("Ingestion page returned no usable items offset : {}", offset);
                }
                pause();
            }
        } catch (RuntimeException e) {
            runs.fail(runId, e.getMessage());
            log.error("Ingestion failed runId : {} (will resume from last offset)", runId, e);
            throw e;
        }
        runs.finish(runId);
        log.info("Ingestion finished provider : {} created : {} updated : {}", provider.name(), created, updated);
        return new IngestionResult(provider.name(), gameVersion, created, updated, false);
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

    private int[] upsertPage(List<ExternalPlayer> items, short gameVersion) {
        Map<Long, BasePlayerEntity> existing = basePlayerRepository
                .findByEaResourceIdIn(items.stream().map(ExternalPlayer::externalId).toList()).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getEaResourceId, Function.identity()));
        Map<Long, PlayerRatingEntity> ratings = playerRatingRepository
                .findByBasePlayerIdInAndGameVersionId(existing.values().stream().map(BasePlayerEntity::getId).toList(), gameVersion)
                .stream().collect(Collectors.toMap(PlayerRatingEntity::getBasePlayerId, Function.identity()));
        Map<String, NationalityEntity> nationalities = new HashMap<>();
        Map<String, LeagueEntity> leagues = new HashMap<>();
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
            base = basePlayerRepository.save(base);
            PlayerRatingEntity rating = ratings.getOrDefault(base.getId(), new PlayerRatingEntity());
            rating.setBasePlayerId(base.getId());
            rating.setGameVersionId(gameVersion);
            applyRating(rating, player, base, leagues);
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
                             Map<String, LeagueEntity> leagues) {
        rating.setOverall((short) player.overall());
        rating.setPositions(player.positions());
        rating.setWeakFoot(toShort(player.weakFoot()));
        rating.setSkillMoves(toShort(player.skillMoves()));
        rating.setPlaystyles(player.playstyles());
        rating.setAccelerate(acceleRateDeriver.derive(Objects.isNull(base.getHeightCm()) ? null : base.getHeightCm().intValue(),
                player.gender(), player.attrs()).map(AcceleRate::name).orElse(null));
        ratingAttrAccessor.write(rating, player.attrs());
        rating.setClubId(club(player, leagues));
    }

    private Integer club(ExternalPlayer player, Map<String, LeagueEntity> leagues) {
        if (Objects.isNull(player.clubName())) {
            return null;
        }
        Integer leagueId = Objects.isNull(player.leagueName()) ? null
                : leagues.computeIfAbsent(player.leagueName(), this::league).getId();
        ClubEntity club = clubRepository.findByNameAndLeagueId(player.clubName(), leagueId).orElseGet(() -> {
            ClubEntity created = new ClubEntity();
            created.setName(player.clubName());
            created.setLeagueId(leagueId);
            return created;
        });
        club.setGender((short) player.gender());
        return clubRepository.save(club).getId();
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
