package com.fcareer.fitengine.service;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.AssignedSlot;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.DepthEntry;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.ScoutQuery;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.model.response.WeakLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SquadFitService {

    private static final int NOT_ASSIGNED = -1;
    private static final double MIRROR_MARGIN = 0.5;

    private final RoleFitService roleFitService;
    private final IntentFitService intentFitService;
    private final HungarianSolver hungarianSolver;
    private final RulesEngine rulesEngine;
    private final NlgService nlgService;
    private final FitEngineProperties properties;
    private final SetupModifier setupModifier;
    private final RoleFitV3Service roleFitV3Service;

    public SquadFitResponse solve(List<FitPlayer> squad, List<IntentProfile> slots, List<TacticRuleDefinition> rules) {
        return solve(squad, slots, rules, "v1");
    }

    public SquadFitResponse solve(List<FitPlayer> squad, List<IntentProfile> slots, List<TacticRuleDefinition> rules, String engineVersion) {
        int playerCount = squad.size();
        int slotCount = slots.size();
        RoleFitResponse[][] roleFits = new RoleFitResponse[playerCount][slotCount];
        IntentFitResponse[][] intentFits = new IntentFitResponse[playerCount][slotCount];
        double[][] combined = new double[playerCount][slotCount];
        double[][] setupDeltas = new double[playerCount][slotCount];

        for (int p = 0; p < playerCount; p++) {
            for (int s = 0; s < slotCount; s++) {
                IntentProfile slot = slots.get(s);
                roleFits[p][s] = FitEngineV3.VERSION.equals(engineVersion)
                        ? roleFitV3Service.calculate(squad.get(p), slot.getResolvedRole(), slot, slot.getSlotPosition())
                        : roleFitService.calculate(squad.get(p), slot.getResolvedRole(), slot, slot.getSlotPosition());
                intentFits[p][s] = intentFitService.calculate(squad.get(p), slot);
                setupDeltas[p][s] = setupModifier.delta(squad.get(p), slot);
                combined[p][s] = combine(roleFits[p][s], intentFits[p][s], slot) + setupDeltas[p][s];
            }
        }

        int[] playerToSlot = hungarianSolver.maximize(combined);
        int[] slotToPlayer = invert(playerToSlot, slotCount);
        Set<Integer> starters = new HashSet<>();
        for (int s = 0; s < slotCount; s++) {
            if (slotToPlayer[s] != NOT_ASSIGNED) {
                starters.add(slotToPlayer[s]);
            }
        }

        List<SlotAssignment> assignments = new ArrayList<>();
        List<AssignedSlot> assignedSlots = new ArrayList<>();
        for (int s = 0; s < slotCount; s++) {
            IntentProfile slot = slots.get(s);
            int p = slotToPlayer[s];
            if (p == NOT_ASSIGNED) {
                assignments.add(emptyAssignment(slot));
                assignedSlots.add(new AssignedSlot(slot, null));
                continue;
            }
            FitPlayer player = squad.get(p);
            assignedSlots.add(new AssignedSlot(slot, player));
            assignments.add(SlotAssignment.builder()
                    .slotId(slot.getSlotId())
                    .position(slot.getSlotPosition())
                    .roleId(slot.getResolvedRole().getId())
                    .roleName(slot.getResolvedRole().getName())
                    .tags(tagIds(slot))
                    .playerId(player.getId())
                    .playerName(player.getName())
                    .overall(player.getOverall())
                    .combined(round1(combined[p][s]))
                    .setupDelta(Objects.isNull(slot.getSetup()) ? null : round1(setupDeltas[p][s]))
                    .roleFit(roleFits[p][s])
                    .intentFit(slot.hasTags() ? intentFits[p][s] : null)
                    .depth(depth(squad, starters, combined, roleFits, s))
                    .build());
        }

        double roleAverage = averageOf(assignments.stream()
                .filter(a -> Objects.nonNull(a.getRoleFit()))
                .mapToDouble(a -> a.getRoleFit().getScore())
                .average());
        Double setupDelta = slots.stream().anyMatch(s -> Objects.nonNull(s.getSetup()))
                ? round1(IntStream.range(0, slotCount).filter(s -> slotToPlayer[s] != NOT_ASSIGNED)
                        .mapToDouble(s -> setupDeltas[slotToPlayer[s]][s]).average().orElse(0))
                : null;
        double squadFit = Objects.isNull(setupDelta) ? roleAverage : round1(Math.max(0, Math.min(100, roleAverage + setupDelta)));
        OptionalDouble intentAverage = assignments.stream()
                .filter(a -> Objects.nonNull(a.getIntentFit()))
                .mapToDouble(a -> a.getIntentFit().getScore())
                .average();
        Double intentFit = intentAverage.isPresent() ? round1(intentAverage.getAsDouble()) : null;

        List<Long> bench = IntStream.range(0, playerCount)
                .filter(p -> !starters.contains(p))
                .mapToObj(p -> squad.get(p).getId())
                .toList();

        log.info("SquadFit solved slots : {} players : {} squadFit : {}", slotCount, playerCount, squadFit);
        return SquadFitResponse.builder()
                .squadFit(squadFit)
                .setupDelta(setupDelta)
                .intentFit(intentFit)
                .summary(nlgService.squadSummary(squadFit, intentFit))
                .slots(assignments)
                .weakLinks(weakLinks(assignments, slots))
                .rules(rulesEngine.evaluate(assignedSlots, rules))
                .attackPattern(nlgService.attackPattern(attackPatterns(slots)))
                .bench(bench)
                .build();
    }

    /** Taktiği olduğu gibi ve sağ-sol aynalanmış haliyle çözer; kadroya daha uygun olanı döner. */
    public SquadFitResponse solveBest(List<FitPlayer> squad, List<IntentProfile> slots, List<TacticRuleDefinition> rules) {
        return solveBest(squad, slots, rules, "v1");
    }

    public SquadFitResponse solveBest(List<FitPlayer> squad, List<IntentProfile> slots, List<TacticRuleDefinition> rules, String engineVersion) {
        SquadFitResponse original = solve(squad, slots, rules, engineVersion);
        if (!TacticMirror.isMirrorable(slots)) {
            return original;
        }
        SquadFitResponse flipped = solve(squad, TacticMirror.mirror(slots), rules, engineVersion);
        if (overall(flipped) > overall(original) + MIRROR_MARGIN) {
            flipped.setMirrored(true);
            log.info("Tactic mirrored original : {} mirrored : {}", overall(original), overall(flipped));
            return flipped;
        }
        return original;
    }

    private double overall(SquadFitResponse response) {
        FitEngineProperties.Squad config = properties.getSquad();
        return Objects.isNull(response.getIntentFit()) ? response.getSquadFit()
                : config.getRoleFitWeight() * response.getSquadFit() + config.getIntentFitWeight() * response.getIntentFit();
    }

    public double combine(RoleFitResponse roleFit, IntentFitResponse intentFit, IntentProfile slot) {
        if (!slot.hasTags()) {
            return roleFit.getScore();
        }
        FitEngineProperties.Squad config = properties.getSquad();
        return config.getRoleFitWeight() * roleFit.getScore() + config.getIntentFitWeight() * intentFit.getScore();
    }

    private int[] invert(int[] playerToSlot, int slotCount) {
        int[] slotToPlayer = new int[slotCount];
        Arrays.fill(slotToPlayer, NOT_ASSIGNED);
        for (int p = 0; p < playerToSlot.length; p++) {
            if (playerToSlot[p] != NOT_ASSIGNED) {
                slotToPlayer[playerToSlot[p]] = p;
            }
        }
        return slotToPlayer;
    }

    private List<DepthEntry> depth(List<FitPlayer> squad, Set<Integer> starters, double[][] combined,
                                   RoleFitResponse[][] roleFits, int slotIndex) {
        return IntStream.range(0, squad.size())
                .filter(p -> !starters.contains(p))
                .boxed()
                .sorted(Comparator.comparingDouble((Integer p) -> combined[p][slotIndex]).reversed())
                .limit(properties.getSquad().getDepthCount())
                .map(p -> DepthEntry.builder()
                        .playerId(squad.get(p).getId())
                        .playerName(squad.get(p).getName())
                        .combined(round1(combined[p][slotIndex]))
                        .roleFitScore(roleFits[p][slotIndex].getScore())
                        .badge(roleFits[p][slotIndex].getBadge())
                        .build())
                .toList();
    }

    private SlotAssignment emptyAssignment(IntentProfile slot) {
        return SlotAssignment.builder()
                .slotId(slot.getSlotId())
                .position(slot.getSlotPosition())
                .roleId(slot.getResolvedRole().getId())
                .roleName(slot.getResolvedRole().getName())
                .tags(tagIds(slot))
                .depth(List.of())
                .build();
    }

    private List<WeakLink> weakLinks(List<SlotAssignment> assignments, List<IntentProfile> slots) {
        Map<String, IntentProfile> slotById = new LinkedHashMap<>();
        slots.forEach(s -> slotById.put(s.getSlotId(), s));
        return assignments.stream()
                .filter(a -> Objects.isNull(a.getRoleFit()) || a.getRoleFit().getBadge() != WeaponState.GREEN)
                .sorted(Comparator.comparingDouble(this::assignmentScore))
                .limit(properties.getSquad().getWeakLinkCount())
                .map(a -> toWeakLink(a, slotById.get(a.getSlotId())))
                .toList();
    }

    private double assignmentScore(SlotAssignment assignment) {
        return Objects.isNull(assignment.getRoleFit()) ? -1 : assignment.getRoleFit().getScore();
    }

    private WeakLink toWeakLink(SlotAssignment assignment, IntentProfile slot) {
        ScoutQuery scoutQuery = scoutQuery(slot);
        if (Objects.isNull(assignment.getRoleFit())) {
            return WeakLink.builder()
                    .slotId(assignment.getSlotId())
                    .roleId(assignment.getRoleId())
                    .score(0)
                    .badge(WeaponState.RED)
                    .sentence(nlgService.emptySlot(assignment.getSlotId()))
                    .scoutQuery(scoutQuery)
                    .build();
        }
        RoleFitResponse roleFit = assignment.getRoleFit();
        return WeakLink.builder()
                .slotId(assignment.getSlotId())
                .playerId(assignment.getPlayerId())
                .playerName(assignment.getPlayerName())
                .roleId(assignment.getRoleId())
                .score(roleFit.getScore())
                .badge(roleFit.getBadge())
                .sentence(nlgService.weakLink(assignment.getSlotId(), assignment.getPlayerName(),
                        assignment.getRoleName(), roleFit.getScore(), roleFit.getBadgeText()))
                .scoutQuery(scoutQuery)
                .build();
    }

    public ScoutQuery scoutQuery(IntentProfile slot) {
        Map<String, Integer> minAttrs = new LinkedHashMap<>();
        for (Weapon weapon : slot.getResolvedRole().getWeapons()) {
            minAttrs.put(weapon.getAttr().getKey(), weapon.getHardMin());
        }
        if (slot.hasTags()) {
            for (FitTag tag : slot.getTags()) {
                minAttrs.merge(tag.primaryWeapon().getKey(), tag.getExpectedMin(), Math::max);
            }
        }
        return ScoutQuery.builder()
                .positions(List.of(slot.getSlotPosition()))
                .minAttrs(minAttrs)
                .accelerate(slot.getResolvedRole().getAcceleRates().stream().map(Enum::name).sorted().toList())
                .build();
    }

    private List<NlgService.SlotPattern> attackPatterns(List<IntentProfile> slots) {
        List<NlgService.SlotPattern> patterns = new ArrayList<>();
        for (IntentProfile slot : slots) {
            if (!slot.hasTags()) {
                continue;
            }
            slot.getTags().stream()
                    .map(FitTag::getNlgTr)
                    .filter(Objects::nonNull)
                    .forEach(sentence -> patterns.add(new NlgService.SlotPattern(slot.getSlotId(), sentence)));
        }
        return patterns;
    }

    private List<String> tagIds(IntentProfile slot) {
        if (!slot.hasTags()) {
            return List.of();
        }
        return slot.getTags().stream().map(FitTag::getId).toList();
    }

    private double averageOf(OptionalDouble value) {
        return value.isPresent() ? round1(value.getAsDouble()) : 0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
