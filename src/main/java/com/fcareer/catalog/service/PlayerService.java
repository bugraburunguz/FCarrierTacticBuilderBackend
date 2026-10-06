package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.RatingColumns;
import com.fcareer.catalog.repository.CareerPlayerRatingRepository;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.model.response.SimilarPlayerResponse;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.model.PageResponse;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.ApiConstants.DEFAULT_PAGE_SIZE;
import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private static final String PLAYER = "player";
    private static final String GOALKEEPER = "GK";
    private static final int SIMILAR_OVERALL_RANGE = 10;
    private static final double SIMILARITY_PENALTY = 2.2;

    private final CatalogProperties catalogProperties;
    private final PlayerSearchRepository playerSearchRepository;
    private final BasePlayerRepository basePlayerRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final CareerPlayerRatingRepository careerPlayerRatingRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final PlayerMapper playerMapper;

    public PageResponse<PlayerSummaryResponse> search(PlayerSearchRequest request) {
        request.setGameVersionId(resolveGameVersion(request.getGameVersionId()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getSize(), MAX_PAGE_SIZE));
        request.setPage(Math.max(request.getPage(), 0));
        return PageResponse.<PlayerSummaryResponse>builder()
                .items(playerSearchRepository.search(request))
                .page(request.getPage())
                .size(request.getSize())
                .totalItems(playerSearchRepository.count(request))
                .build();
    }

    public PlayerDetailResponse detail(Long playerId, Short gameVersionId) {
        short gv = resolveGameVersion(gameVersionId);
        BasePlayerEntity base = basePlayerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        PlayerRatingEntity rating = playerRatingRepository.findByBasePlayerIdAndGameVersionId(playerId, gv)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        PlayerSummaryResponse summary = summaryOf(playerId, gv);
        return playerMapper.toDetail(summary, base, rating);
    }

    public List<FitPlayer> fitPlayers(List<Long> playerIds, Short gameVersionId) {
        return fitPlayers(playerIds, gameVersionId, null);
    }

    public List<FitPlayer> fitPlayers(List<Long> playerIds, Short gameVersionId, Long careerId) {
        short gv = resolveGameVersion(gameVersionId);
        Map<Long, BasePlayerEntity> bases = basePlayerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getId, Function.identity()));
        Map<Long, RatingColumns> ratings = new HashMap<>();
        playerRatingRepository.findByBasePlayerIdInAndGameVersionId(playerIds, gv).forEach(r -> ratings.put(r.getBasePlayerId(), r));
        if (Objects.nonNull(careerId)) {
            careerPlayerRatingRepository.findByCareerIdAndBasePlayerIdInAndGameVersionId(careerId, playerIds, gv)
                    .forEach(r -> ratings.put(r.getBasePlayerId(), r));
        }
        Map<Long, PlayerValuationEntity> valuations = playerValuationRepository
                .findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(playerIds, gv, ValuationSource.MODELED)
                .stream().collect(Collectors.toMap(PlayerValuationEntity::getBasePlayerId, Function.identity()));
        return playerIds.stream().map(id -> {
            if (!bases.containsKey(id) || !ratings.containsKey(id)) {
                throw new NotFoundException(PLAYER, id);
            }
            return playerMapper.toFitPlayer(bases.get(id), ratings.get(id), valuations.get(id));
        }).toList();
    }

    public List<SimilarPlayerResponse> similar(Long playerId, Short gameVersionId, Integer ageMax, Integer potentialMin, int limit) {
        short gv = resolveGameVersion(gameVersionId);
        FitPlayer target = fitPlayers(List.of(playerId), gv).get(0);
        PlayerSummaryResponse summary = summaryOf(playerId, gv);
        boolean keeper = target.getPositions().contains(GOALKEEPER);
        PlayerSearchRequest request = PlayerSearchRequest.builder()
                .gameVersionId(gv).gender(Objects.isNull(summary.getGender()) ? null : summary.getGender().shortValue())
                .positions(List.of(target.getPositions().get(0)))
                .overallMin(target.getOverall() - SIMILAR_OVERALL_RANGE).overallMax(target.getOverall() + SIMILAR_OVERALL_RANGE)
                .ageMax(ageMax).potentialMin(potentialMin).excludeIds(List.of(playerId))
                .sort("overall").size(MAX_PAGE_SIZE).build();
        Map<Long, PlayerSummaryResponse> pool = playerSearchRepository.search(request).stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
        List<Attr> axes = java.util.Arrays.stream(Attr.values()).filter(a -> a.getKey().startsWith("GK") == keeper).toList();
        return fitPlayers(new java.util.ArrayList<>(pool.keySet()), gv).stream()
                .map(candidate -> SimilarPlayerResponse.builder().player(pool.get(candidate.getId()))
                        .similarity(similarity(target, candidate, axes)).build())
                .sorted(java.util.Comparator.comparingDouble(SimilarPlayerResponse::getSimilarity).reversed())
                .limit(limit)
                .toList();
    }

    private static double similarity(FitPlayer a, FitPlayer b, List<Attr> axes) {
        double sum = 0;
        for (Attr attr : axes) {
            double diff = a.attr(attr) - b.attr(attr);
            sum += diff * diff;
        }
        double distance = Math.sqrt(sum / axes.size());
        return Math.round(Math.max(0, 100 - distance * SIMILARITY_PENALTY) * 10.0) / 10.0;
    }

    public Map<Long, PlayerSummaryResponse> summaries(List<Long> ids, Short gameVersionId) {
        return summaries(ids, gameVersionId, null);
    }

    public Map<Long, PlayerSummaryResponse> summaries(List<Long> ids, Short gameVersionId, Long careerId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        PlayerSearchRequest request = new PlayerSearchRequest();
        request.setCareerId(careerId);
        request.setGameVersionId(resolveGameVersion(gameVersionId));
        request.setIds(ids);
        request.setSize(ids.size());
        return playerSearchRepository.search(request).stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, Function.identity()));
    }

    private PlayerSummaryResponse summaryOf(Long playerId, short gameVersionId) {
        PlayerSearchRequest request = new PlayerSearchRequest();
        request.setGameVersionId(gameVersionId);
        request.setSize(1);
        request.setIdFilter(playerId);
        return playerSearchRepository.search(request).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
    }

    private short resolveGameVersion(Short requested) {
        return Objects.isNull(requested) ? catalogProperties.getDefaultGameVersion() : requested;
    }
}
