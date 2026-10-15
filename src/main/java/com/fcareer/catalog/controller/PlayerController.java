package com.fcareer.catalog.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.model.response.SimilarPlayerResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.response.PlayerRoleFit;
import com.fcareer.fitengine.service.FitService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.model.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/players")
@RequiredArgsConstructor
public class PlayerController {

    private static final int MAX_SIMILAR = 20;
    private static final int MAX_ASSET_IDS = 100;
    private static final String MIN_SUFFIX = "_min";
    private static final String MAX_SUFFIX = "_max";
    private static final String ORDER_DESC = "desc";
    private static final Set<String> RESERVED_BOUNDS = Set.of("ovr", "pot", "value", "age", "wf", "sm");

    private final PlayerService playerService;
    private final CareerService careerService;
    private final FitService fitService;
    private final com.fcareer.career.desk.SpoilerPolicy spoilerPolicy;
    private final com.fcareer.recommendation.pipeline.CareerPlayerList careerPlayerList;

    @GetMapping
    public PageResponse<PlayerSummaryResponse> search(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(name = "career", required = false) Long careerId,
            @RequestParam(name = "team", required = false) Integer teamId,
            @RequestParam(name = "free_agent", required = false, defaultValue = "false") boolean freeAgent,
            @RequestParam(name = "gv", required = false) Short gameVersionId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "pos", required = false) List<String> positions,
            @RequestParam(name = "ps", required = false) List<String> playstyles,
            @RequestParam(name = "at", required = false) List<String> accelerate,
            @RequestParam(name = "ovr_min", required = false) Integer overallMin,
            @RequestParam(name = "ovr_max", required = false) Integer overallMax,
            @RequestParam(name = "pot_min", required = false) Integer potentialMin,
            @RequestParam(name = "pot_max", required = false) Integer potentialMax,
            @RequestParam(name = "value_max", required = false) Long valueMax,
            @RequestParam(name = "value_min", required = false) Long valueMin,
            @RequestParam(name = "on_loan", required = false, defaultValue = "false") boolean onLoan,
            @RequestParam(name = "contract_months", required = false) Integer contractMonths,
            @RequestParam(name = "bucket", required = false) List<String> buckets,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "role_pos", required = false) String rolePosition,
            @RequestParam(name = "age_min", required = false) Integer ageMin,
            @RequestParam(name = "age_max", required = false) Integer ageMax,
            @RequestParam(name = "wf_min", required = false) Integer weakFootMin,
            @RequestParam(name = "sm_min", required = false) Integer skillMovesMin,
            @RequestParam(name = "foot", required = false) String foot,
            @RequestParam(name = "club", required = false) Integer clubId,
            @RequestParam(name = "league", required = false) Integer leagueId,
            @RequestParam(name = "nat", required = false) Integer nationalityId,
            @RequestParam(name = "gender", required = false) Short gender,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false, defaultValue = ORDER_DESC) String order,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "0") int size,
            @RequestParam Map<String, String> allParams) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
        Map<String, Integer> attrMin = new HashMap<>();
        Map<String, Integer> attrMax = new HashMap<>();
        allParams.forEach((key, value) -> collectAttrBound(key, value, attrMin, attrMax));
        PlayerSearchRequest request = PlayerSearchRequest.builder()
                .gameVersionId(gameVersionId)
                .query(query)
                .positions(orEmpty(positions))
                .playstyles(orEmpty(playstyles))
                .accelerate(orEmpty(accelerate))
                .attrMin(attrMin)
                .attrMax(attrMax)
                .overallMin(overallMin)
                .overallMax(overallMax)
                .potentialMin(spoilerPolicy.minBand(careerId, potentialMin))
                .potentialMax(spoilerPolicy.maxBand(careerId, potentialMax))
                .valueMax(valueMax)
                .valueMin(valueMin)
                .onLoan(onLoan)
                .contractMonthsLeft(contractMonths)
                .ageMin(ageMin)
                .ageMax(ageMax)
                .weakFootMin(weakFootMin)
                .skillMovesMin(skillMovesMin)
                .foot(foot)
                .clubId(clubId)
                .careerId(careerId)
                .teamId(teamId)
                .freeAgent(freeAgent)
                .leagueId(leagueId)
                .nationalityId(nationalityId)
                .gender(gender)
                .sort(sortFor(careerId, sort))
                .ascending(!ORDER_DESC.equalsIgnoreCase(order))
                .page(page)
                .size(size)
                .build();
        // V3-06a: kariyer havuzunda ulaşılabilirlik kovası (sütun + süzgeç) ve FitEngineV3 rol sıralaması; katalogda mevcut davranış.
        PageResponse<PlayerSummaryResponse> page0 = careerId == null ? playerService.search(request)
                : careerPlayerList.search(user == null ? null : user.id(), request, buckets, role, rolePosition);
        spoilerPolicy.maskAll(careerId, page0.getItems());
        return page0;
    }

    /** V3-06a: kariyer listesinde hangi süzgeçlerin verisi var (oyun tarihi, bütçe, sözleşme, kiralık); veri yoksa web süzgeci gizler. */
    @GetMapping("/facets")
    public com.fcareer.catalog.repository.CareerPlayerRepository.CareerFacets facets(@AuthenticationPrincipal AuthUser user, @RequestParam(name = "career") Long careerId) {
        return careerPlayerList.facets(user == null ? null : user.id(), careerId);
    }

    @GetMapping("/{id}/roles")
    public List<PlayerRoleFit> roles(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long id,
                                     @RequestParam(name = "gv", required = false) Short gameVersionId,
                                     @RequestParam(name = "career", required = false) Long careerId,
                                     @RequestParam(name = "engine", required = false) String engineVersion) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
        return fitService.rolesFor(id, gameVersionId, careerId, engineVersion);
    }

    /** V3-06b: kariyer oyuncu sayfasının sözleşme ve değer verisi; kova ve alım sonrası bütçe öneri hattından. */
    @GetMapping("/{id}/contract")
    public com.fcareer.recommendation.pipeline.model.CareerPlayerContractResponse contract(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                                                           @RequestParam(name = "career") Long careerId) {
        return careerPlayerList.contract(user == null ? null : user.id(), careerId, id);
    }

    /** Aralık modunda potansiyele göre sıralama sıra bilgisiyle tam değeri sızdırır; OVR sıralamasına düşer. */
    private String sortFor(Long careerId, String sort) {
        return spoilerPolicy.ranged(careerId) && sort != null && sort.toLowerCase(java.util.Locale.ROOT).contains("pot") ? "overall" : sort;
    }

    @GetMapping("/{id}/similar")
    public List<SimilarPlayerResponse> similar(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                               @RequestParam(name = "career", required = false) Long careerId,
                                               @RequestParam(name = "gv", required = false) Short gameVersionId,
                                               @RequestParam(name = "age_max", required = false) Integer ageMax,
                                               @RequestParam(name = "pot_min", required = false) Integer potentialMin,
                                               @RequestParam(name = "limit", required = false, defaultValue = "8") int limit) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
        List<SimilarPlayerResponse> similar = playerService.similar(id, gameVersionId, careerId, ageMax, spoilerPolicy.minBand(careerId, potentialMin), Math.max(1, Math.min(limit, MAX_SIMILAR)));
        similar.forEach(s -> spoilerPolicy.mask(careerId, s.getPlayer()));
        return similar;
    }

    @GetMapping("/{id}")
    public PlayerDetailResponse detail(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long id,
                                       @RequestParam(name = "gv", required = false) Short gameVersionId,
                                       @RequestParam(name = "career", required = false) Long careerId) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
        PlayerDetailResponse detail = playerService.detail(id, gameVersionId, careerId);
        spoilerPolicy.mask(careerId, detail.getSummary());
        return detail;
    }

    /** V3-05c-c: SBC ödül kartı (EA verisinden çizilir) için toplu assetId → katalog adı; en çok MAX_ASSET_IDS, bilinmeyen kimlik yanıtta yok. */
    @GetMapping("/assets")
    public List<com.fcareer.catalog.repository.BasePlayerRepository.AssetName> namesByAsset(@RequestParam(name = "id") List<Long> assetIds) {
        if (assetIds.size() > MAX_ASSET_IDS) {
            throw new InvalidTacticException("id");
        }
        return playerService.namesByAsset(new java.util.LinkedHashSet<>(assetIds));
    }

    /** UT oyuncu sayfası (/ut/players/:assetId): EA oyuncu kimliğiyle katalog detayı (kariyersiz; potansiyel maskesi kapalı katalog görünümü). */
    @GetMapping("/asset/{assetId}")
    public PlayerDetailResponse detailByAsset(@PathVariable Long assetId, @RequestParam(name = "gv", required = false) Short gameVersionId) {
        PlayerDetailResponse detail = playerService.detailByAsset(assetId, gameVersionId);
        spoilerPolicy.mask(null, detail.getSummary());
        return detail;
    }

    private void collectAttrBound(String key, String value, Map<String, Integer> attrMin, Map<String, Integer> attrMax) {
        boolean min = key.endsWith(MIN_SUFFIX);
        boolean max = key.endsWith(MAX_SUFFIX);
        if (!min && !max) {
            return;
        }
        String attrName = key.substring(0, key.length() - MIN_SUFFIX.length());
        if (RESERVED_BOUNDS.contains(attrName)) {
            return;
        }
        try {
            String normalized = PlayerMapper.normalizeAttr(attrName).getKey();
            (min ? attrMin : attrMax).put(normalized, Integer.parseInt(value));
        } catch (IllegalArgumentException e) {
            throw new InvalidTacticException("filter " + key);
        }
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
