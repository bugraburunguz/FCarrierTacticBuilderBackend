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
        int created = 0;
        int updated = 0;
        int total = Integer.MAX_VALUE;
        for (int offset = 0; offset < total; offset += pageSize) {
            if (limit > 0 && offset >= limit) {
                break;
            }
            ExternalPlayerPage page = provider.fetch(offset, pageSize);
            total = page.totalItems();
            List<ExternalPlayer> items = limit > 0 ? page.items().stream().limit(limit - offset).toList() : page.items();
            int[] counts = transactionTemplate.execute(status -> upsertPage(items, gameVersion));
            created += counts[0];
            updated += counts[1];
            log.info("Ingestion page done provider : {} offset : {} total : {}", provider.name(), offset, total);
            if (page.items().isEmpty() && total > offset + pageSize) {
                log.warn("Ingestion page returned no usable items offset : {}", offset);
            }
            pause();
        }
        log.info("Ingestion finished provider : {} created : {} updated : {}", provider.name(), created, updated);
        return new IngestionResult(provider.name(), gameVersion, created, updated, false);
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
            applyRating(rating, player, leagues);
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
        base.setHeightCm(toShort(player.heightCm()));
        base.setWeightKg(toShort(player.weightKg()));
        base.setPreferredFoot(player.preferredFoot());
        base.setNationalityId(Objects.isNull(player.nationality()) ? null : cache
                .computeIfAbsent(player.nationality(), this::nationality).getId());
    }

    private void applyRating(PlayerRatingEntity rating, ExternalPlayer player, Map<String, LeagueEntity> leagues) {
        rating.setOverall((short) player.overall());
        rating.setPositions(player.positions());
        rating.setWeakFoot(toShort(player.weakFoot()));
        rating.setSkillMoves(toShort(player.skillMoves()));
        rating.setPlaystyles(player.playstyles());
        AcceleRate acceleRate = acceleRateDeriver.derive(player.heightCm(), player.attrs());
        rating.setAccelerate(acceleRate.name());
        ratingAttrAccessor.write(rating, player.attrs());
        rating.setClubId(club(player, leagues));
    }

    private Integer club(ExternalPlayer player, Map<String, LeagueEntity> leagues) {
        if (Objects.isNull(player.clubName())) {
            return null;
        }
        Integer leagueId = Objects.isNull(player.leagueName()) ? null
                : leagues.computeIfAbsent(player.leagueName(), this::league).getId();
        return clubRepository.findByNameAndLeagueId(player.clubName(), leagueId)
                .orElseGet(() -> {
                    ClubEntity club = new ClubEntity();
                    club.setName(player.clubName());
                    club.setLeagueId(leagueId);
                    return clubRepository.save(club);
                }).getId();
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
