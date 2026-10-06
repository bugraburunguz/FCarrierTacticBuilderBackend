package com.fcareer.recommendation.service;

import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.ClubRepository;
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
import com.fcareer.recommendation.model.request.SlotScope;
import com.fcareer.recommendation.model.request.SlotViewRequest;
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
import java.util.OptionalDouble;
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
    private static final int PREVIEW_LIMIT = 8;
    private static final Short DEFAULT_PREVIEW_GENDER = 0;
    private static final String FREE_AGENTS = "Free Agents";
    private static final int SQUAD_CORE = 18;
    private static final int LEAGUE_QUALITY_MIN_OVERALL = 60;
    private static final int LEAGUE_QUALITY_MIN_PLAYERS = 100;
    private static final double LEAGUE_QUALITY_ABOVE = 1.5;
    private static final double LEAGUE_QUALITY_BELOW = 6.0;
    private static final int MARKET_OVERALL_BELOW = 2;
    private static final int MARKET_OVERALL_ABOVE = 6;

    private final PlayerService playerService;
    private final PlayerSearchRepository playerSearchRepository;
    private final ClubRepository clubRepository;
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
        Short gender = null;
        Long budget = request.getBudgetMax();
        if (Objects.nonNull(request.getCareerId())) {
            CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
            squadIds = squad.playerIds();
            gameVersion = squad.gameVersionId();
            gender = squad.gender();
            budget = Objects.isNull(budget) ? squad.budgetEur() : budget;
        }
        PlayerSearchRequest search = candidateSearch(role, intent, request, squadIds, gameVersion, budget, gender);
        List<PlayerSummaryResponse> candidates = playerService.search(search).getItems();
        Map<Long, PlayerSummaryResponse> summaries = candidates.stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, c -> c));
        Long finalBudget = budget;
        Short finalVersion = gameVersion;
        return creditGuard.runCharged(userId, billingProperties.getRecommendCost(), RECOMMEND,
                () -> rank(request, role, intent, summaries, finalBudget, finalVersion));
    }

    public RecommendationResponse preview(RecommendSlotRequest request) {
        FitRole role = roleService.getFitRole(request.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, request.getPosition(), null, request.getRoleId(), request.getTags());
        Short gender = Objects.isNull(request.getGender()) ? DEFAULT_PREVIEW_GENDER : request.getGender();
        PlayerSearchRequest search = candidateSearch(role, intent, request, List.of(), request.getGameVersionId(), null, gender);
        Map<Long, PlayerSummaryResponse> summaries = playerService.search(search).getItems().stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, c -> c));
        RecommendSlotRequest capped = RecommendSlotRequest.builder().roleId(request.getRoleId()).position(request.getPosition())
                .limit(Math.min(Objects.isNull(request.getLimit()) ? PREVIEW_LIMIT : request.getLimit(), PREVIEW_LIMIT)).build();
        return rank(capped, role, intent, summaries, null, request.getGameVersionId());
    }

    public RecommendationResponse slotView(Long userId, SlotViewRequest view) {
        RecommendSlotRequest request = view.getSlot();
        FitRole role = roleService.getFitRole(request.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, request.getPosition(), null, request.getRoleId(), request.getTags());
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, view.getCareerId());
        if (view.getScope() == SlotScope.SQUAD && squad.playerIds().isEmpty()) {
            return RecommendationResponse.builder().roleId(role.getId()).position(request.getPosition()).items(List.of()).build();
        }
        PlayerSearchRequest search = switch (view.getScope()) {
            case SQUAD -> PlayerSearchRequest.builder().gameVersionId(squad.gameVersionId()).gender(squad.gender())
                    .ids(squad.playerIds()).sort("overall").size(CANDIDATE_POOL).build();
            case LEAGUE -> {
                PlayerSearchRequest league = candidateSearch(role, intent, request, squad.playerIds(), squad.gameVersionId(), null, squad.gender());
                league.setLeagueId(request.getLeagueId());
                yield league;
            }
            case MARKET -> marketSearch(role, intent, request, squad);
        };
        Map<Long, PlayerSummaryResponse> summaries = playerService.search(search).getItems().stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, c -> c));
        RecommendSlotRequest capped = RecommendSlotRequest.builder().careerId(view.getCareerId()).roleId(request.getRoleId()).position(request.getPosition())
                .limit(Math.min(Objects.isNull(request.getLimit()) ? PREVIEW_LIMIT : request.getLimit(), PREVIEW_LIMIT)).build();
        return rank(capped, role, intent, summaries, search.getValueMax(), squad.gameVersionId());
    }

    private PlayerSearchRequest marketSearch(FitRole role, IntentProfile intent, RecommendSlotRequest request,
                                             CareerService.CareerSquad squad) {
        Long budget = Objects.isNull(request.getBudgetMax()) ? squad.budgetEur() : request.getBudgetMax();
        PlayerSearchRequest search = candidateSearch(role, intent, request, squad.playerIds(), squad.gameVersionId(), budget, squad.gender());
        OptionalDouble squadAverage = squadCoreAverage(squad);
        if (squadAverage.isPresent()) {
            long average = Math.round(squadAverage.getAsDouble());
            search.setOverallMin((int) average - MARKET_OVERALL_BELOW);
            search.setOverallMax((int) average + MARKET_OVERALL_ABOVE);
        }
        search.setLeagueIds(reachableLeagues(squad));
        return search;
    }

    OptionalDouble squadCoreAverage(CareerService.CareerSquad squad) {
        if (squad.playerIds().isEmpty()) {
            return OptionalDouble.empty();
        }
        PlayerSearchRequest own = PlayerSearchRequest.builder().gameVersionId(squad.gameVersionId()).gender(squad.gender())
                .ids(squad.playerIds()).sort("overall").size(CANDIDATE_POOL).build();
        return playerService.search(own).getItems().stream()
                .mapToInt(PlayerSummaryResponse::getOverall)
                .sorted().skip(Math.max(0, squad.playerIds().size() - SQUAD_CORE))
                .average();
    }

    List<Integer> reachableLeagues(CareerService.CareerSquad squad) {
        Integer ownLeague = Objects.isNull(squad.clubId()) ? null
                : clubRepository.findById(squad.clubId()).map(club -> club.getLeagueId()).orElse(null);
        List<PlayerSearchRepository.LeagueQuality> leagues = playerSearchRepository.leagueQuality(
                squad.gameVersionId(), squad.gender(), LEAGUE_QUALITY_MIN_OVERALL, LEAGUE_QUALITY_MIN_PLAYERS);
        double ownQuality = leagues.stream().filter(l -> Objects.equals(l.id(), ownLeague))
                .mapToDouble(PlayerSearchRepository.LeagueQuality::averageOverall).findFirst().orElse(Double.NaN);
        if (Double.isNaN(ownQuality)) {
            return List.of();
        }
        return leagues.stream()
                .filter(l -> Objects.equals(l.id(), ownLeague) || FREE_AGENTS.equals(l.name())
                        || (l.averageOverall() <= ownQuality + LEAGUE_QUALITY_ABOVE && l.averageOverall() >= ownQuality - LEAGUE_QUALITY_BELOW))
                .map(PlayerSearchRepository.LeagueQuality::id)
                .toList();
    }

    PlayerSearchRequest candidateSearch(FitRole role, IntentProfile intent, RecommendSlotRequest request,
                                                List<Long> squadIds, Short gameVersion, Long budget, Short gender) {
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
        Map<Long, FitPlayer> players = playerService.fitPlayers(ids, gameVersion, request.getCareerId()).stream()
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

    RecommendationItem evaluate(FitPlayer player, PlayerSummaryResponse summary, FitRole role,
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
