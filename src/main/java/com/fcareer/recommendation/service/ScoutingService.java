package com.fcareer.recommendation.service;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.model.request.ScoutingSearchRequest;
import com.fcareer.recommendation.model.request.TransferTargetRequest;
import com.fcareer.recommendation.model.response.Acquisition;
import com.fcareer.recommendation.model.response.Feasibility;
import com.fcareer.recommendation.model.response.RecommendationItem;
import com.fcareer.recommendation.model.response.ScoutingCandidate;
import com.fcareer.recommendation.model.response.ScoutingResponse;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoutingService {

    private static final String SLOT_ID = "SLOT";
    private static final int DEFAULT_LIMIT = 20;
    private static final int POOL_SIZE = 40;
    private static final int DREAM_OVERALL_ABOVE = 6;
    private static final int PROSPECT_AGE_MAX = 21;
    private static final int POTENTIAL_ABOVE_AVERAGE = 4;
    private static final double FEE_SCALE = 100_000.0;
    private static final double OPPORTUNITY_SCALE = 10.0;
    private static final String SORT_OPPORTUNITY = "opportunity";
    private static final String SORT_POTENTIAL = "potential";
    private static final String SOURCE_MODELED = "MODELED";

    private final CareerService careerService;
    private final PlayerService playerService;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final RecommendationService recommendationService;
    private final TransferTargetService transferTargetService;
    private final ClubRepository clubRepository;

    public ScoutingResponse search(Long userId, Long careerId, ScoutingSearchRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        OptionalDouble average = recommendationService.squadCoreAverage(squad);
        if (average.isEmpty()) {
            return ScoutingResponse.builder().budgetEur(squad.budgetEur()).items(List.of()).build();
        }
        int rounded = (int) Math.round(average.getAsDouble());
        FitRole role = roleService.getFitRole(request.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, request.getPosition(), null, request.getRoleId(), request.getTags(), request.getSetup());
        RecommendSlotRequest base = RecommendSlotRequest.builder().roleId(request.getRoleId()).position(request.getPosition()).tags(request.getTags()).build();
        TransferTargetRequest pool = new TransferTargetRequest();
        pool.setCareerId(careerId);

        Map<Long, PlayerSummaryResponse> candidates = new LinkedHashMap<>();
        Set<Long> inBand = new HashSet<>();
        Set<Integer> leagues = new HashSet<>(recommendationService.reachableLeagues(squad));
        if (hasExplicitTarget(request)) {
            Integer targetLeague = targetLeague(request);
            transferTargetService.search(restrict(explicit(role, intent, base, squad, careerId, request), request), candidates, new HashSet<>());
            if (Objects.nonNull(targetLeague) && leagues.contains(targetLeague)) {
                inBand.addAll(candidates.keySet());
            }
        } else {
            transferTargetService.search(restrict(transferTargetService.band(role, intent, base, squad, pool, rounded, leagues), request), candidates, inBand);
            transferTargetService.search(restrict(transferTargetService.free(role, intent, base, squad, pool, rounded), request), candidates, inBand);
            transferTargetService.search(restrict(transferTargetService.prospects(role, intent, base, squad, pool, rounded), request), candidates, new HashSet<>());
            if (request.isDream()) {
                transferTargetService.search(restrict(dream(role, intent, base, squad, careerId, rounded), request), candidates, new HashSet<>());
            }
        }
        candidates.keySet().removeAll(squad.playerIds());

        Map<Long, FitPlayer> fit = playerService.fitPlayers(new ArrayList<>(candidates.keySet()), squad.gameVersionId(), careerId).stream()
                .collect(Collectors.toMap(FitPlayer::getId, p -> p, (a, b) -> a));
        List<ScoutingCandidate> all = candidates.values().stream()
                .map(summary -> toCandidate(fit.get(summary.getId()), summary, role, intent, request.getPosition(), inBand, squad.budgetEur(), rounded))
                .toList();
        List<ScoutingCandidate> visible = request.isDream() ? all : all.stream().filter(c -> c.getFeasibility() != Feasibility.UNREALISTIC).toList();
        int limit = Objects.isNull(request.getLimit()) ? DEFAULT_LIMIT : request.getLimit();
        log.info("Scouting search careerId : {} position : {} candidates : {} dream : {}", careerId, request.getPosition(), all.size(), request.isDream());
        return ScoutingResponse.builder()
                .squadAverage(average.getAsDouble())
                .budgetEur(squad.budgetEur())
                .hiddenUnrealistic(all.size() - visible.size())
                .items(visible.stream().sorted(comparator(request.getSort(), request.isDream())).limit(limit).toList())
                .build();
    }

    private PlayerSearchRequest dream(FitRole role, IntentProfile intent, RecommendSlotRequest base, CareerService.CareerSquad squad, Long careerId, int average) {
        PlayerSearchRequest search = recommendationService.candidateSearch(role, intent, base, squad.playerIds(), squad.gameVersionId(), null, squad.gender());
        search.setCareerId(careerId);
        search.setSize(POOL_SIZE);
        search.setOverallMin(average + DREAM_OVERALL_ABOVE);
        return search;
    }

    private boolean hasExplicitTarget(ScoutingSearchRequest request) {
        return Objects.nonNull(request.getLeagueId()) || Objects.nonNull(request.getClubId());
    }

    private Integer targetLeague(ScoutingSearchRequest request) {
        if (Objects.nonNull(request.getLeagueId())) {
            return request.getLeagueId();
        }
        return clubRepository.findById(request.getClubId()).map(club -> club.getLeagueId()).orElse(null);
    }

    private PlayerSearchRequest explicit(FitRole role, IntentProfile intent, RecommendSlotRequest base, CareerService.CareerSquad squad, Long careerId,
                                         ScoutingSearchRequest request) {
        PlayerSearchRequest search = recommendationService.candidateSearch(role, intent, base, squad.playerIds(), squad.gameVersionId(), null, squad.gender());
        search.setCareerId(careerId);
        search.setSize(POOL_SIZE);
        search.setLeagueId(request.getLeagueId());
        search.setClubId(request.getClubId());
        return search;
    }

    private PlayerSearchRequest restrict(PlayerSearchRequest search, ScoutingSearchRequest request) {
        search.setOverallMin(maxOf(search.getOverallMin(), request.getOverallMin()));
        search.setOverallMax(minOf(search.getOverallMax(), request.getOverallMax()));
        search.setPotentialMin(maxOf(search.getPotentialMin(), request.getPotentialMin()));
        search.setPotentialMax(minOf(search.getPotentialMax(), request.getPotentialMax()));
        if (Objects.nonNull(request.getAgeMax())) {
            search.setAgeMax(minOf(search.getAgeMax(), request.getAgeMax()));
        }
        return search;
    }

    private static Integer maxOf(Integer a, Integer b) {
        if (Objects.isNull(a)) {
            return b;
        }
        return Objects.isNull(b) ? a : Integer.valueOf(Math.max(a, b));
    }

    private static Integer minOf(Integer a, Integer b) {
        if (Objects.isNull(a)) {
            return b;
        }
        return Objects.isNull(b) ? a : Integer.valueOf(Math.min(a, b));
    }

    private ScoutingCandidate toCandidate(FitPlayer player, PlayerSummaryResponse summary, FitRole role, IntentProfile intent, String position,
                                          Set<Long> inBand, Long budget, int average) {
        RecommendationItem item = recommendationService.evaluate(player, summary, role, intent, position, null);
        boolean banded = inBand.contains(summary.getId());
        Acquisition acquisition = acquisition(summary, banded, average);
        Double ratio = valueRatio(summary.getValueEur(), budget);
        Feasibility feasibility = FeasibilityClassifier.classify(acquisition, banded, ratio, summary.getOverall() - average);
        long fee = acquisition == Acquisition.FREE || Objects.isNull(summary.getValueEur()) ? 0L : summary.getValueEur();
        return ScoutingCandidate.builder()
                .player(summary)
                .roleFit(item.getRoleFit())
                .combined(item.getCombined())
                .acquisition(acquisition)
                .feasibility(feasibility)
                .estimatedFee(Objects.isNull(summary.getValueEur()) ? null : fee)
                .valueSource(SOURCE_MODELED)
                .valueRatio(ratio)
                .opportunity(opportunity(item.getCombined(), fee))
                .potential(isPotential(summary, average))
                .build();
    }

    private Acquisition acquisition(PlayerSummaryResponse summary, boolean banded, int average) {
        if (summary.isFreeAgent()) {
            return Acquisition.FREE;
        }
        if (banded) {
            return Acquisition.BUY;
        }
        return isPotential(summary, average) ? Acquisition.LOAN : Acquisition.BUY;
    }

    public static Double valueRatio(Long value, Long budget) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (Objects.isNull(budget) || budget <= 0) {
            return null; // bütçe bilinmiyor: oran da bilinmiyor, aday "gerçekçi değil" diye gizlenmez (ENG-02)
        }
        return value.doubleValue() / budget;
    }

    static double opportunity(double combined, long fee) {
        double divisor = 1.0 + Math.log10(1.0 + fee / FEE_SCALE);
        return Math.round(combined * OPPORTUNITY_SCALE / divisor) / OPPORTUNITY_SCALE;
    }

    private boolean isPotential(PlayerSummaryResponse summary, int average) {
        return Objects.nonNull(summary.getAge()) && summary.getAge() <= PROSPECT_AGE_MAX
                && Objects.nonNull(summary.getPotential()) && summary.getPotential() >= average + POTENTIAL_ABOVE_AVERAGE;
    }

    private Comparator<ScoutingCandidate> comparator(String sort, boolean dream) {
        Comparator<ScoutingCandidate> byKey = switch (Objects.isNull(sort) ? "" : sort) {
            case SORT_OPPORTUNITY -> Comparator.comparingDouble(ScoutingCandidate::getOpportunity).reversed();
            case SORT_POTENTIAL -> Comparator.comparingInt((ScoutingCandidate c) -> Objects.requireNonNullElse(c.getPlayer().getPotential(), 0)).reversed();
            default -> Comparator.comparingDouble(ScoutingCandidate::getCombined).reversed();
        };
        return dream ? Comparator.comparing((ScoutingCandidate c) -> c.getFeasibility() == Feasibility.UNREALISTIC).thenComparing(byKey) : byKey;
    }
}
