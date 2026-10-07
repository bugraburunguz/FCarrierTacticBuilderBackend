package com.fcareer.recommendation.service;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.model.request.TransferTargetRequest;
import com.fcareer.recommendation.model.request.TransferTargetRequest.TargetSlot;
import com.fcareer.recommendation.model.response.Acquisition;
import com.fcareer.recommendation.model.response.PositionTargets;
import com.fcareer.recommendation.model.response.RecommendationItem;
import com.fcareer.recommendation.model.response.TransferTarget;
import com.fcareer.recommendation.model.response.TransferTargetsResponse;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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
public class TransferTargetService {

    private static final String SLOT_ID = "SLOT";
    private static final int DEFAULT_PER_POSITION = 3;
    private static final int POOL_PER_QUERY = 40;
    private static final int BAND_BELOW = 2;
    private static final int BAND_ABOVE = 6;
    private static final int FREE_ABOVE = 8;
    private static final int PROSPECT_AGE_MAX = 21;
    private static final int PROSPECT_OVERALL_BELOW = 12;
    private static final int PROSPECT_OVERALL_ABOVE = 2;
    private static final int LOW_OVERALL_AGE_MAX = 25;
    private static final int LOW_OVERALL_BELOW = 12;
    private static final int LOW_OVERALL_GAP = 2;
    private static final int POTENTIAL_ABOVE_AVERAGE = 4;

    private final CareerService careerService;
    private final PlayerService playerService;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final RecommendationService recommendationService;

    public TransferTargetsResponse targets(Long userId, TransferTargetRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        OptionalDouble average = recommendationService.squadCoreAverage(squad);
        if (average.isEmpty()) {
            return TransferTargetsResponse.builder().budgetEur(squad.budgetEur()).positions(List.of()).build();
        }
        int perPosition = Objects.isNull(request.getPerPosition()) ? DEFAULT_PER_POSITION : request.getPerPosition();
        int rounded = (int) Math.round(average.getAsDouble());
        Set<Integer> bandLeagues = new HashSet<>(recommendationService.reachableLeagues(squad));
        Set<Long> used = new HashSet<>(squad.playerIds());
        List<PositionTargets> positions = new ArrayList<>();
        for (TargetSlot slot : distinctByPosition(request.getSlots())) {
            positions.add(forSlot(request, squad, slot, rounded, bandLeagues, perPosition, used));
        }
        log.info("Transfer targets careerId : {} positions : {}", request.getCareerId(), positions.size());
        return TransferTargetsResponse.builder().squadAverage(average.getAsDouble()).budgetEur(squad.budgetEur()).positions(positions).build();
    }

    private List<TargetSlot> distinctByPosition(List<TargetSlot> slots) {
        Map<String, TargetSlot> byPosition = new LinkedHashMap<>();
        slots.forEach(s -> byPosition.putIfAbsent(s.getPosition(), s));
        return new ArrayList<>(byPosition.values());
    }

    private PositionTargets forSlot(TransferTargetRequest request, CareerService.CareerSquad squad, TargetSlot slot, int average,
                                    Set<Integer> bandLeagues, int perPosition, Set<Long> used) {
        FitRole role = roleService.getFitRole(slot.getRoleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, slot.getPosition(), null, slot.getRoleId(), slot.getTags(), request.getSetup());
        RecommendSlotRequest base = RecommendSlotRequest.builder().roleId(slot.getRoleId()).position(slot.getPosition()).tags(slot.getTags()).build();

        Map<Long, PlayerSummaryResponse> pool = new LinkedHashMap<>();
        Set<Long> bandIds = new HashSet<>();
        search(band(role, intent, base, squad, request, average, bandLeagues), pool, bandIds);
        search(free(role, intent, base, squad, request, average), pool, bandIds);
        search(prospects(role, intent, base, squad, request, average), pool, new HashSet<>());
        search(lowOverallHighPotential(role, intent, base, squad, request, average), pool, new HashSet<>());
        pool.keySet().removeAll(used);

        Map<Long, FitPlayer> fit = playerService.fitPlayers(new ArrayList<>(pool.keySet()), squad.gameVersionId(), request.getCareerId()).stream()
                .collect(Collectors.toMap(FitPlayer::getId, p -> p, (a, b) -> a));
        List<TransferTarget> ranked = pool.values().stream()
                .map(summary -> toTarget(fit.get(summary.getId()), summary, role, intent, slot.getPosition(), bandIds, average))
                .sorted(Comparator.comparingDouble(TransferTarget::getCombined).reversed())
                .toList();
        List<TransferTarget> picked = pick(ranked, perPosition);
        picked.forEach(t -> used.add(t.getPlayer().getId()));
        return PositionTargets.builder().position(slot.getPosition()).roleId(slot.getRoleId()).targets(picked).build();
    }

    private PlayerSearchRequest band(FitRole role, IntentProfile intent, RecommendSlotRequest base, CareerService.CareerSquad squad,
                                     TransferTargetRequest request, int average, Set<Integer> leagues) {
        PlayerSearchRequest search = recommendationService.candidateSearch(role, intent, base, squad.playerIds(), squad.gameVersionId(),
                squad.budgetEur(), squad.gender());
        search.setCareerId(request.getCareerId());
        search.setSize(POOL_PER_QUERY);
        search.setOverallMin(average - BAND_BELOW);
        search.setOverallMax(average + BAND_ABOVE);
        search.setLeagueIds(new ArrayList<>(leagues));
        return search;
    }

    private PlayerSearchRequest free(FitRole role, IntentProfile intent, RecommendSlotRequest base, CareerService.CareerSquad squad,
                                     TransferTargetRequest request, int average) {
        PlayerSearchRequest search = recommendationService.candidateSearch(role, intent, base, squad.playerIds(), squad.gameVersionId(),
                null, squad.gender());
        search.setCareerId(request.getCareerId());
        search.setSize(POOL_PER_QUERY);
        search.setFreeAgent(true);
        search.setOverallMin(average - BAND_BELOW);
        search.setOverallMax(average + FREE_ABOVE);
        return search;
    }

    private PlayerSearchRequest prospects(FitRole role, IntentProfile intent, RecommendSlotRequest base, CareerService.CareerSquad squad,
                                          TransferTargetRequest request, int average) {
        PlayerSearchRequest search = recommendationService.candidateSearch(role, intent, base, squad.playerIds(), squad.gameVersionId(),
                null, squad.gender());
        search.setCareerId(request.getCareerId());
        search.setSize(POOL_PER_QUERY);
        search.setAttrMin(new HashMap<>());
        search.setAgeMax(PROSPECT_AGE_MAX);
        search.setOverallMin(average - PROSPECT_OVERALL_BELOW);
        search.setOverallMax(average + PROSPECT_OVERALL_ABOVE);
        search.setPotentialMin(average + POTENTIAL_ABOVE_AVERAGE);
        search.setSort("potential");
        return search;
    }

    private PlayerSearchRequest lowOverallHighPotential(FitRole role, IntentProfile intent, RecommendSlotRequest base,
                                                        CareerService.CareerSquad squad, TransferTargetRequest request, int average) {
        PlayerSearchRequest search = prospects(role, intent, base, squad, request, average);
        search.setAgeMax(LOW_OVERALL_AGE_MAX);
        search.setOverallMin(average - LOW_OVERALL_BELOW);
        search.setOverallMax(average - LOW_OVERALL_GAP);
        return search;
    }

    private void search(PlayerSearchRequest search, Map<Long, PlayerSummaryResponse> pool, Set<Long> collect) {
        for (PlayerSummaryResponse summary : playerService.search(search).getItems()) {
            pool.putIfAbsent(summary.getId(), summary);
            collect.add(summary.getId());
        }
    }

    private TransferTarget toTarget(FitPlayer player, PlayerSummaryResponse summary, FitRole role, IntentProfile intent, String position,
                                    Set<Long> bandIds, int average) {
        RecommendationItem item = recommendationService.evaluate(player, summary, role, intent, position, null);
        Acquisition acquisition = summary.isFreeAgent() ? Acquisition.FREE : bandIds.contains(summary.getId()) ? Acquisition.BUY : Acquisition.LOAN;
        return TransferTarget.builder().player(summary).roleFit(item.getRoleFit()).combined(item.getCombined())
                .acquisition(acquisition).reason(reason(summary, acquisition, average)).build();
    }

    private String reason(PlayerSummaryResponse summary, Acquisition acquisition, int average) {
        return switch (acquisition) {
            case FREE -> "Serbest oyuncu — bonservis bedeli yok";
            case BUY -> "Lige ve kadro seviyene uygun" + (Objects.isNull(summary.getLeague()) ? "" : " (" + summary.getLeague() + ")");
            case LOAN -> Objects.nonNull(summary.getAge()) && summary.getAge() <= PROSPECT_AGE_MAX
                    ? "Genç yetenek (" + summary.getAge() + " yaş, POT " + summary.getPotential() + ") — kiralık hedefi"
                    : "Düşük overall, yüksek potansiyel (POT " + summary.getPotential() + ", kadro ort. " + average + ")";
        };
    }

    private List<TransferTarget> pick(List<TransferTarget> ranked, int limit) {
        List<TransferTarget> picked = new ArrayList<>();
        Map<Acquisition, TransferTarget> bestByType = new EnumMap<>(Acquisition.class);
        ranked.forEach(t -> bestByType.putIfAbsent(t.getAcquisition(), t));
        bestByType.values().stream().sorted(Comparator.comparingDouble(TransferTarget::getCombined).reversed())
                .limit(limit).forEach(picked::add);
        for (TransferTarget target : ranked) {
            if (picked.size() >= limit) {
                break;
            }
            if (!picked.contains(target)) {
                picked.add(target);
            }
        }
        picked.sort(Comparator.comparingDouble(TransferTarget::getCombined).reversed());
        return picked;
    }
}
