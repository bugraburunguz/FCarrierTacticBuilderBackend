package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.RatingColumns;
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
import com.fcareer.catalog.repository.CareerPlayerRepository;
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
    private final com.fcareer.catalog.repository.CareerPlayerRepository careerPlayerRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final PlayerMapper playerMapper;
    private final com.fcareer.versioning.service.VersioningService versioningService;

    public PageResponse<PlayerSummaryResponse> search(PlayerSearchRequest request) {
        request.setGameVersionId(resolveGameVersion(request.getGameVersionId()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getSize(), MAX_PAGE_SIZE));
        request.setPage(Math.max(request.getPage(), 0));
        return PageResponse.<PlayerSummaryResponse>builder()
                .items(enrich(playerSearchRepository.search(request), request.getGameVersionId()))
                .page(request.getPage())
                .size(request.getSize())
                .totalItems(playerSearchRepository.count(request))
                .build();
    }

    /** EA oyuncu kimliğiyle (assetId = ea_resource_id) katalog detayı; kariyere özel üretilmiş oyuncular dışlanır. */
    public PlayerDetailResponse detailByAsset(Long assetId, Short gameVersionId) {
        BasePlayerEntity base = basePlayerRepository.findByEaResourceId(assetId).orElseThrow(() -> new NotFoundException(PLAYER, assetId));
        return detail(base.getId(), gameVersionId);
    }

    public PlayerDetailResponse detail(Long playerId, Short gameVersionId) {
        return detail(playerId, gameVersionId, null);
    }

    public PlayerDetailResponse detail(Long playerId, Short gameVersionId, Long careerId) {
        short gv = resolveGameVersion(gameVersionId);
        BasePlayerEntity base = basePlayerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        if (!basePlayerRepository.findForeignOwnedIds(List.of(playerId), careerId).isEmpty()) {
            throw new NotFoundException(PLAYER, playerId);
        }
        RatingColumns rating;
        if (Objects.nonNull(careerId)) {
            CareerPlayerRepository.Row row = careerPlayerRepository.byIds(careerId, List.of(playerId)).get(playerId);
            if (Objects.isNull(row)) {
                throw new NotFoundException(PLAYER, playerId);
            }
            rating = playerMapper.toRating(row);
        } else {
            rating = playerRatingRepository.findByBasePlayerIdAndGameVersionId(playerId, gv).orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        }
        PlayerSummaryResponse summary = enrich(List.of(summaryOf(playerId, gv, careerId)), gv).get(0);
        PlayerDetailResponse detail = playerMapper.toDetail(summary, base, rating);
        detail.setDiff(versioningService.diffFor(playerId, gv).orElse(null));
        return detail;
    }

    public List<FitPlayer> fitPlayers(List<Long> playerIds, Short gameVersionId) {
        return fitPlayers(playerIds, gameVersionId, null);
    }

    public List<FitPlayer> fitPlayers(List<Long> playerIds, Short gameVersionId, Long careerId) {
        short gv = resolveGameVersion(gameVersionId);
        if (!playerIds.isEmpty()) {
            List<Long> foreign = basePlayerRepository.findForeignOwnedIds(playerIds, careerId);
            if (!foreign.isEmpty()) {
                throw new NotFoundException(PLAYER, foreign.get(0));
            }
        }
        Map<Long, BasePlayerEntity> bases = basePlayerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getId, Function.identity()));
        Map<Long, RatingColumns> ratings = new HashMap<>();
        Map<Long, CareerPlayerRepository.Row> careerRows = Objects.nonNull(careerId) ? careerPlayerRepository.byIds(careerId, playerIds) : Map.of();
        if (Objects.isNull(careerId)) {
            playerRatingRepository.findByBasePlayerIdInAndGameVersionId(playerIds, gv).forEach(r -> ratings.put(r.getBasePlayerId(), r));
        }
        Map<Long, PlayerValuationEntity> valuations = playerValuationRepository
                .findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(playerIds, gv, ValuationSource.MODELED)
                .stream().collect(Collectors.toMap(PlayerValuationEntity::getBasePlayerId, Function.identity()));
        java.time.LocalDate gameDate = playerSearchRepository.careerGameDate(careerId).orElse(null);
        return playerIds.stream().map(id -> {
            boolean hasRating = Objects.nonNull(careerId) ? careerRows.containsKey(id) : ratings.containsKey(id);
            if (!bases.containsKey(id) || !hasRating) {
                throw new NotFoundException(PLAYER, id);
            }
            return Objects.nonNull(careerId) ? playerMapper.toFitPlayer(bases.get(id), careerRows.get(id), valuations.get(id), gameDate)
                    : playerMapper.toFitPlayer(bases.get(id), ratings.get(id), valuations.get(id), gameDate);
        }).toList();
    }

    public List<SimilarPlayerResponse> similar(Long playerId, Short gameVersionId, Long careerId, Integer ageMax, Integer potentialMin, int limit) {
        short gv = resolveGameVersion(gameVersionId);
        FitPlayer target = fitPlayers(List.of(playerId), gv, careerId).get(0);
        PlayerSummaryResponse summary = summaryOf(playerId, gv, careerId);
        boolean keeper = target.getPositions().contains(GOALKEEPER);
        PlayerSearchRequest request = PlayerSearchRequest.builder()
                .gameVersionId(gv).careerId(careerId).gender(Objects.isNull(summary.getGender()) ? null : summary.getGender().shortValue())
                .positions(List.of(target.getPositions().get(0)))
                .overallMin(target.getOverall() - SIMILAR_OVERALL_RANGE).overallMax(target.getOverall() + SIMILAR_OVERALL_RANGE)
                .ageMax(ageMax).potentialMin(potentialMin).excludeIds(List.of(playerId))
                .sort("overall").size(MAX_PAGE_SIZE).build();
        Map<Long, PlayerSummaryResponse> pool = playerSearchRepository.search(request).stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
        List<Attr> axes = java.util.Arrays.stream(Attr.values()).filter(a -> a.getKey().startsWith("GK") == keeper).toList();
        return fitPlayers(new java.util.ArrayList<>(pool.keySet()), gv, careerId).stream()
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
        return summaryOf(playerId, gameVersionId, null);
    }

    private PlayerSummaryResponse summaryOf(Long playerId, short gameVersionId, Long careerId) {
        PlayerSearchRequest request = new PlayerSearchRequest();
        request.setCareerId(careerId);
        request.setGameVersionId(gameVersionId);
        request.setSize(1);
        request.setIdFilter(playerId);
        return playerSearchRepository.search(request).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
    }

    /** Yüz adresi (şablondan) ve son sürümde değişmiş olma bilgisi eklenir; diff precompute edilmiştir, burada yalnızca okunur. */
    private List<PlayerSummaryResponse> enrich(List<PlayerSummaryResponse> items, short gameVersionId) {
        String template = faceTemplate();
        Map<Long, com.fcareer.versioning.model.VersionModels.ChangeSummary> changes = versioningService.summaries(
                items.stream().map(PlayerSummaryResponse::getId).toList(), gameVersionId);
        for (PlayerSummaryResponse item : items) {
            if (Objects.nonNull(template) && !template.isBlank() && Objects.nonNull(item.getFaceAssetId())) {
                item.setFaceUrl(template.replace("{id}", String.valueOf(item.getFaceAssetId())));
            }
            com.fcareer.versioning.model.VersionModels.ChangeSummary change = changes.get(item.getId());
            if (Objects.nonNull(change)) {
                item.setChanged(true);
                item.setChangeSummary(change);
            }
        }
        return items;
    }

    /** Yanıtlara konan yüz adresi kendi proxy'mizdir; kaynak şablonu boşsa (takedown) hiç adres dönülmez. */
    private String faceTemplate() {
        String upstream = catalogProperties.getFaceUrlTemplate();
        return Objects.isNull(upstream) || upstream.isBlank() ? null : catalogProperties.getFaceProxyPath();
    }

    public short resolveGameVersion(Short requested) {
        return Objects.isNull(requested) ? catalogProperties.getDefaultGameVersion() : requested;
    }
}
