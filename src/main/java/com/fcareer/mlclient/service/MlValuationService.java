package com.fcareer.mlclient.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.LeagueEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.LeagueRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.mlclient.client.MlClient;
import com.fcareer.mlclient.configuration.MlProperties;
import com.fcareer.mlclient.model.MlPlayerFeatures;
import com.fcareer.mlclient.model.MlPredictResponse;
import com.fcareer.mlclient.model.MlPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlValuationService {

    private final PlayerRatingRepository playerRatingRepository;
    private final BasePlayerRepository basePlayerRepository;
    private final ClubRepository clubRepository;
    private final LeagueRepository leagueRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final MlFeatureBuilder featureBuilder;
    private final MlClient mlClient;
    private final MlProperties properties;
    private final CatalogProperties catalogProperties;
    private final TransactionTemplate transactionTemplate;

    public int run(Short gameVersionId) {
        short gameVersion = Objects.isNull(gameVersionId) ? catalogProperties.getDefaultGameVersion() : gameVersionId;
        int written = 0;
        int page = 0;
        Page<PlayerRatingEntity> ratings;
        do {
            ratings = playerRatingRepository.findByGameVersionId(gameVersion,
                    PageRequest.of(page++, properties.getBatchSize(), Sort.by("id")));
            if (!ratings.isEmpty()) {
                written += processBatch(ratings.getContent(), gameVersion);
            }
        } while (ratings.hasNext());
        log.info("ML valuations written gameVersion : {} count : {}", gameVersion, written);
        return written;
    }

    private int processBatch(List<PlayerRatingEntity> ratings, short gameVersion) {
        Map<Long, BasePlayerEntity> bases = basePlayerRepository
                .findAllById(ratings.stream().map(PlayerRatingEntity::getBasePlayerId).toList()).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getId, Function.identity()));
        Map<Integer, Integer> clubTier = clubTiers(ratings);
        List<MlPlayerFeatures> features = ratings.stream()
                .map(r -> featureBuilder.build(bases.get(r.getBasePlayerId()), r, clubTier.get(r.getClubId())))
                .toList();

        MlPredictResponse response = mlClient.predict(features);
        if (response.isFc27PotFlag()) {
            log.warn("ML potential model is not calibrated for FC27 (fc27_pot_flag)");
        }
        Integer written = transactionTemplate.execute(status -> store(response.getPredictions(), gameVersion));
        return Objects.isNull(written) ? 0 : written;
    }

    private int store(List<MlPrediction> predictions, short gameVersion) {
        List<Long> ids = predictions.stream().map(MlPrediction::getPlayerId).toList();
        Map<Long, PlayerValuationEntity> existing = playerValuationRepository
                .findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(ids, gameVersion, ValuationSource.MODELED)
                .stream().collect(Collectors.toMap(PlayerValuationEntity::getBasePlayerId, Function.identity()));
        List<PlayerValuationEntity> toSave = predictions.stream().map(p -> {
            PlayerValuationEntity valuation = existing.getOrDefault(p.getPlayerId(), new PlayerValuationEntity());
            valuation.setBasePlayerId(p.getPlayerId());
            valuation.setGameVersionId(gameVersion);
            valuation.setSource(ValuationSource.MODELED);
            valuation.setPotential(p.getPotential().shortValue());
            valuation.setValueEur(p.getValue());
            valuation.setModelVersion(p.getModelVersion());
            return valuation;
        }).toList();
        playerValuationRepository.saveAll(toSave);
        return toSave.size();
    }

    private Map<Integer, Integer> clubTiers(List<PlayerRatingEntity> ratings) {
        List<Integer> clubIds = ratings.stream().map(PlayerRatingEntity::getClubId).filter(Objects::nonNull).distinct().toList();
        Map<Integer, ClubEntity> clubs = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(ClubEntity::getId, Function.identity()));
        Map<Integer, Short> leagueTier = leagueRepository.findAllById(clubs.values().stream()
                        .map(ClubEntity::getLeagueId).filter(Objects::nonNull).distinct().toList()).stream()
                .filter(l -> Objects.nonNull(l.getTier()))
                .collect(Collectors.toMap(LeagueEntity::getId, LeagueEntity::getTier));
        return clubs.values().stream()
                .filter(c -> Objects.nonNull(c.getLeagueId()) && leagueTier.containsKey(c.getLeagueId()))
                .collect(Collectors.toMap(ClubEntity::getId, c -> leagueTier.get(c.getLeagueId()).intValue()));
    }
}
