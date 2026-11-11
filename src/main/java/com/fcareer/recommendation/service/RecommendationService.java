package com.fcareer.recommendation.service;

import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.IntentFitService;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.SetupModifier;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.model.response.RecommendationItem;
import com.fcareer.recommendation.model.response.RecommendationResponse;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String SLOT_ID = "SLOT";
    private static final int CANDIDATE_POOL = MAX_PAGE_SIZE;
    private static final double MAX_SCORE = 100.0;
    private static final int DEFAULT_LIMIT = 10;
    private static final int PREVIEW_LIMIT = 8;
    private static final int PREVIEW_ITEMS = 3;
    private static final Short DEFAULT_PREVIEW_GENDER = 0;

    private final PlayerService playerService;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final RoleFitService roleFitService;
    private final IntentFitService intentFitService;
    private final SquadFitService squadFitService;
    private final FitEngineProperties fitEngineProperties;
    private final SetupModifier setupModifier;

    /** Anonim önizleme (BE-SEC-01, D-01): ilk 3 aday, gerekçe/silah/intent/setup içgörüsü yok; tam sonuç yalnız ücretli akışlarda. */
    public RecommendationResponse preview(RecommendSlotRequest request) {
        RecommendSlotRequest limited = RecommendSlotRequest.builder().roleId(request.getRoleId()).position(request.getPosition()).tags(request.getTags())
                .setup(request.getSetup()).gender(request.getGender()).gameVersionId(request.getGameVersionId())
                .limit(Objects.isNull(request.getLimit()) || request.getLimit() <= 0 ? PREVIEW_ITEMS : Math.min(request.getLimit(), PREVIEW_ITEMS)).build();
        return withoutPaidInsights(rankPreview(limited));
    }

    private RecommendationResponse rankPreview(RecommendSlotRequest request) {
        FitRole role = roleService.getFitRole(request.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, request.getPosition(), null, request.getRoleId(), request.getTags(), request.getSetup());
        Short gender = Objects.isNull(request.getGender()) ? DEFAULT_PREVIEW_GENDER : request.getGender();
        PlayerSearchRequest search = candidateSearch(role, request, request.getGameVersionId(), gender);
        Map<Long, PlayerSummaryResponse> summaries = playerService.search(search).getItems().stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, c -> c));
        RecommendSlotRequest capped = RecommendSlotRequest.builder().roleId(request.getRoleId()).position(request.getPosition())
                .limit(Math.min(Objects.isNull(request.getLimit()) ? PREVIEW_LIMIT : request.getLimit(), PREVIEW_LIMIT)).build();
        return rank(capped, role, intent, summaries, request.getGameVersionId());
    }

    private static RecommendationResponse withoutPaidInsights(RecommendationResponse full) {
        List<RecommendationItem> items = Objects.isNull(full.getItems()) ? List.of() : full.getItems().stream().map(RecommendationService::stripped).toList();
        return RecommendationResponse.builder().roleId(full.getRoleId()).position(full.getPosition()).budgetMax(full.getBudgetMax())
                .current(Objects.isNull(full.getCurrent()) ? null : stripped(full.getCurrent())).items(items).build();
    }

    private static RecommendationItem stripped(RecommendationItem item) {
        RoleFitResponse fit = item.getRoleFit();
        RoleFitResponse light = Objects.isNull(fit) ? null : RoleFitResponse.builder().playerId(fit.getPlayerId()).playerName(fit.getPlayerName())
                .roleId(fit.getRoleId()).roleName(fit.getRoleName()).score(fit.getScore()).projectedRating(fit.getProjectedRating())
                .badge(fit.getBadge()).outOfPosition(fit.isOutOfPosition()).build();
        return RecommendationItem.builder().player(item.getPlayer()).roleFit(light).combined(item.getCombined()).deltaVsCurrent(item.getDeltaVsCurrent()).build();
    }

    private PlayerSearchRequest candidateSearch(FitRole role, RecommendSlotRequest request, Short gameVersion, Short gender) {
        Set<String> positions = new LinkedHashSet<>(List.of(request.getPosition()));
        positions.addAll(fitEngineProperties.getPositionEquivalents().getOrDefault(request.getPosition(), List.of()));
        Map<String, Integer> minAttrs = new HashMap<>();
        int compensation = fitEngineProperties.getWeapon().getPlaystylePlusCompensation();
        for (Weapon weapon : role.getWeapons()) {
            minAttrs.merge(weapon.getAttr().getKey(), Math.max(0, weapon.getHardMin() - compensation), Math::max);
        }
        return PlayerSearchRequest.builder()
                .gameVersionId(gameVersion)
                .gender(gender)
                .positions(new ArrayList<>(positions))
                .attrMin(minAttrs)
                .includeUnvalued(request.isIncludeUnvalued())
                .excludeIds(List.of())
                .sort("prescore")
                .prescoreWeights(role.getAttrWeights())
                .size(CANDIDATE_POOL)
                .build();
    }

    private RecommendationResponse rank(RecommendSlotRequest request, FitRole role, IntentProfile intent,
                                        Map<Long, PlayerSummaryResponse> summaries, Short gameVersion) {
        int limit = Objects.isNull(request.getLimit()) || request.getLimit() <= 0 ? DEFAULT_LIMIT : request.getLimit();
        List<Long> ids = new ArrayList<>(summaries.keySet());
        Map<Long, FitPlayer> players = playerService.fitPlayers(ids, gameVersion, null).stream()
                .collect(Collectors.toMap(FitPlayer::getId, p -> p, (a, b) -> a));
        List<RecommendationItem> items = summaries.values().stream()
                .map(summary -> evaluate(players.get(summary.getId()), summary, role, intent, request.getPosition()))
                .sorted(Comparator.comparingDouble(RecommendationItem::getCombined).reversed())
                .limit(limit)
                .toList();
        log.info("Slot recommendation roleId : {} candidates : {} returned : {}", role.getId(), summaries.size(), items.size());
        return RecommendationResponse.builder()
                .roleId(role.getId())
                .position(request.getPosition())
                .items(items)
                .scoutQuery(squadFitService.scoutQuery(intent))
                .build();
    }

    private RecommendationItem evaluate(FitPlayer player, PlayerSummaryResponse summary, FitRole role, IntentProfile intent, String position) {
        RoleFitResponse roleFit = roleFitService.calculate(player, role, intent, position);
        IntentFitResponse intentFit = intent.hasTags() ? intentFitService.calculate(player, intent) : null;
        double setupDelta = setupModifier.delta(player, intent);
        double combined = round1(Math.max(0, Math.min(MAX_SCORE, squadFitService.combine(roleFit, intentFit, intent) + setupDelta)));
        return RecommendationItem.builder()
                .setupDelta(Objects.isNull(intent.getSetup()) ? null : round1(setupDelta))
                .player(summary)
                .roleFit(roleFit)
                .intentFit(intentFit)
                .combined(combined)
                .build();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
