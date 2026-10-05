package com.fcareer.recommendation.service;

import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.PlayerSearchRepository;
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

    private static final String RECOMMEND = "RECOMMEND_SLOT";
    private static final String SLOT_ID = "SLOT";
    private static final int CANDIDATE_POOL = MAX_PAGE_SIZE;
    private static final int DEFAULT_LIMIT = 10;

    private final PlayerService playerService;
    private final PlayerSearchRepository playerSearchRepository;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final CareerService careerService;
    private final RoleFitService roleFitService;
    private final IntentFitService intentFitService;
    private final SquadFitService squadFitService;
    private final CreditGuard creditGuard;
    private final BillingProperties billingProperties;
    private final FitEngineProperties fitEngineProperties;

    public CreditedResponse<RecommendationResponse> recommend(Long userId, RecommendSlotRequest request) {
        FitRole role = roleService.getFitRole(request.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, request.getPosition(), null, request.getRoleId(), request.getTags());
        List<Long> squadIds = List.of();
        Short gameVersion = request.getGameVersionId();
        Long budget = request.getBudgetMax();
        if (Objects.nonNull(request.getCareerId())) {
            CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
            squadIds = squad.playerIds();
            gameVersion = squad.gameVersionId();
            budget = Objects.isNull(budget) ? squad.budgetEur() : budget;
        }
        PlayerSearchRequest search = candidateSearch(role, intent, request, squadIds, gameVersion, budget);
        List<PlayerSummaryResponse> candidates = playerService.search(search).getItems();
        Map<Long, PlayerSummaryResponse> summaries = candidates.stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, c -> c));
        Long finalBudget = budget;
        Short finalVersion = gameVersion;
        return creditGuard.runCharged(userId, billingProperties.getRecommendCost(), RECOMMEND,
                () -> rank(request, role, intent, summaries, finalBudget, finalVersion));
    }

    private PlayerSearchRequest candidateSearch(FitRole role, IntentProfile intent, RecommendSlotRequest request,
                                                List<Long> squadIds, Short gameVersion, Long budget) {
        Set<String> positions = new LinkedHashSet<>(List.of(request.getPosition()));
        positions.addAll(fitEngineProperties.getPositionEquivalents().getOrDefault(request.getPosition(), List.of()));
        Map<String, Integer> minAttrs = new HashMap<>();
        int compensation = fitEngineProperties.getWeapon().getPlaystylePlusCompensation();
        for (Weapon weapon : role.getWeapons()) {
            minAttrs.merge(weapon.getAttr().getKey(), Math.max(0, weapon.getHardMin() - compensation), Math::max);
        }
        return PlayerSearchRequest.builder()
                .gameVersionId(gameVersion)
                .positions(new ArrayList<>(positions))
                .attrMin(minAttrs)
                .valueMax(budget)
                .includeUnvalued(request.isIncludeUnvalued())
                .excludeIds(squadIds)
                .sort("overall")
                .size(CANDIDATE_POOL)
                .build();
    }

    private RecommendationResponse rank(RecommendSlotRequest request, FitRole role, IntentProfile intent,
                                        Map<Long, PlayerSummaryResponse> summaries, Long budget, Short gameVersion) {
        int limit = Objects.isNull(request.getLimit()) || request.getLimit() <= 0 ? DEFAULT_LIMIT : request.getLimit();
        List<Long> ids = new ArrayList<>(summaries.keySet());
        if (Objects.nonNull(request.getCurrentPlayerId())) {
            ids.add(request.getCurrentPlayerId());
        }
        Map<Long, FitPlayer> players = playerService.fitPlayers(ids, gameVersion).stream()
                .collect(Collectors.toMap(FitPlayer::getId, p -> p, (a, b) -> a));

        RecommendationItem current = Objects.isNull(request.getCurrentPlayerId()) ? null
                : evaluate(players.get(request.getCurrentPlayerId()), null, role, intent, request.getPosition(), null);
        Double currentScore = Objects.isNull(current) ? null : current.getCombined();

        List<RecommendationItem> items = summaries.values().stream()
                .map(summary -> evaluate(players.get(summary.getId()), summary, role, intent, request.getPosition(), currentScore))
                .sorted(Comparator.comparingDouble(RecommendationItem::getCombined).reversed())
                .limit(limit)
                .toList();
        log.info("Slot recommendation roleId : {} candidates : {} returned : {}", role.getId(), summaries.size(), items.size());
        return RecommendationResponse.builder()
                .roleId(role.getId())
                .position(request.getPosition())
                .budgetMax(budget)
                .current(current)
                .items(items)
                .scoutQuery(squadFitService.scoutQuery(intent))
                .build();
    }

    private RecommendationItem evaluate(FitPlayer player, PlayerSummaryResponse summary, FitRole role,
                                        IntentProfile intent, String position, Double currentScore) {
        RoleFitResponse roleFit = roleFitService.calculate(player, role, intent, position);
        IntentFitResponse intentFit = intent.hasTags() ? intentFitService.calculate(player, intent) : null;
        double combined = round1(squadFitService.combine(roleFit, intentFit, intent));
        return RecommendationItem.builder()
                .player(summary)
                .roleFit(roleFit)
                .intentFit(intentFit)
                .combined(combined)
                .deltaVsCurrent(Objects.isNull(currentScore) ? null : round1(combined - currentScore))
                .build();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
