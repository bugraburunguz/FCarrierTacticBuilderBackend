package com.fcareer.advisor.service;

import com.fcareer.advisor.model.response.AdvisorResponses.Candidate;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.HungarianSolver;
import com.fcareer.fitengine.service.IntentFitService;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.fitengine.service.TacticMirror;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvisorService {

    private static final double MIRROR_MARGIN = 0.5;
    private static final String STYLE = "STYLE";
    private static final String REPLICA = "REPLICA";
    private static final String PERSONAL = "PERSONAL";

    private final TacticResolver tacticResolver;
    private final FormationRegistry formationRegistry;
    private final RoleFitRouter roleFitRouter;
    private final IntentFitService intentFitService;
    private final SquadFitService squadFitService;
    private final HungarianSolver hungarianSolver;

    Scored scoreStyle(List<FitPlayer> players, PresetResponse style, FormationDefinition formation, Map<String, List<String>> template,
                      TeamSetup override) {
        Map<String, List<String>> bySlot = new LinkedHashMap<>();
        Map<String, String> presetGroups = formationRegistry.get(style.getFormation()).slots().stream()
                .collect(Collectors.toMap(FormationSlot::slotId, FormationSlot::group));
        for (FormationSlot slot : formation.slots()) {
            List<String> nativeTags = style.getSlotTags().get(slot.slotId());
            boolean sameGroup = Objects.nonNull(nativeTags) && slot.group().equals(presetGroups.get(slot.slotId()));
            List<String> tags = sameGroup ? nativeTags : template.getOrDefault(slot.group(), List.of());
            if (!tags.isEmpty()) {
                bySlot.put(slot.slotId(), tags);
            }
        }
        TeamSetup setup = Objects.nonNull(override) ? override : setupOf(style);
        List<IntentProfile> profiles = resolve(formation.id(), null, bySlot, setup);
        return scored(style.getKind().equals(PERSONAL) ? STYLE : style.getKind(), style, formation.id(), profiles, evaluate(players, profiles));
    }

    Scored scored(String kind, PresetResponse preset, String formationId, List<IntentProfile> profiles, Eval eval) {
        Candidate candidate = Candidate.builder().kind(kind).presetId(preset.getId()).name(preset.getName()).formation(formationId)
                .formationLabel(label(formationId)).score(round1(eval.score())).squadFit(round1(eval.squadFit()))
                .intentFit(Objects.isNull(eval.intentFit()) ? null : round1(eval.intentFit()))
                .tactic(toTactic(formationId, REPLICA.equals(kind) ? preset.getId() : null, profiles)).build();
        return new Scored(candidate, profiles);
    }

    Map<String, List<String>> styleTemplate(PresetResponse style, Map<String, FitTag> tagCatalog) {
        FormationDefinition source = formationRegistry.get(style.getFormation());
        Map<String, String> groupBySlot = source.slots().stream().collect(Collectors.toMap(FormationSlot::slotId, FormationSlot::group));
        Map<String, List<String>> template = new LinkedHashMap<>();
        style.getSlotTags().forEach((slotId, tagIds) -> {
            String group = groupBySlot.get(slotId);
            if (Objects.isNull(group)) {
                return;
            }
            List<String> chosen = template.computeIfAbsent(group, g -> new ArrayList<>());
            for (String tagId : tagIds) {
                FitTag tag = tagCatalog.get(tagId);
                boolean conflicts = chosen.stream().anyMatch(c -> tagCatalog.get(c).getConflicts().contains(tagId)
                        || (Objects.nonNull(tag) && tag.getConflicts().contains(c)));
                if (Objects.nonNull(tag) && !chosen.contains(tagId) && !conflicts) {
                    chosen.add(tagId);
                }
            }
        });
        mergeGroupTags(style, template, tagCatalog);
        return template;
    }

    @SuppressWarnings("unchecked")
    private void mergeGroupTags(PresetResponse style, Map<String, List<String>> template, Map<String, FitTag> tagCatalog) {
        Object declared = Objects.isNull(style.getSettings()) ? null : style.getSettings().get("groupTags");
        if (!(declared instanceof Map<?, ?> groups)) {
            return;
        }
        ((Map<String, List<String>>) groups).forEach((group, tagIds) -> {
            List<String> valid = tagIds.stream().filter(tagCatalog::containsKey).toList();
            if (!valid.isEmpty()) {
                template.put(group, new ArrayList<>(valid));
            }
        });
    }

    TeamSetup setupOf(PresetResponse preset) {
        Map<String, Object> settings = preset.getSettings();
        if (Objects.isNull(settings)) {
            return null;
        }
        Object buildUp = settings.get("buildUp");
        Object depth = settings.get("depth");
        if (Objects.isNull(buildUp) && !(depth instanceof Number)) {
            return null;
        }
        return TeamSetup.builder().buildUp(Objects.isNull(buildUp) ? null : buildUp.toString())
                .depth(depth instanceof Number number ? number.intValue() : null).build();
    }

    List<IntentProfile> resolve(String formationId, String presetId, Map<String, List<String>> tagsBySlot) {
        return resolve(formationId, presetId, tagsBySlot, null);
    }

    List<IntentProfile> resolve(String formationId, String presetId, Map<String, List<String>> tagsBySlot, TeamSetup setup) {
        List<SlotSelection> selections = tagsBySlot.entrySet().stream()
                .map(e -> SlotSelection.builder().slotId(e.getKey()).tags(e.getValue()).build()).toList();
        return tacticResolver.resolve(TacticRequest.builder().formation(formationId).presetId(presetId).slots(new ArrayList<>(selections)).setup(setup).build()).slots();
    }

    TacticRequest toTactic(String formationId, String presetId, List<IntentProfile> profiles) {
        List<SlotSelection> slots = profiles.stream().map(p -> SlotSelection.builder().slotId(p.getSlotId())
                .tags(p.getTags().stream().map(FitTag::getId).toList()).roleId(p.getResolvedRole().getId()).build()).toList();
        TeamSetup setup = profiles.isEmpty() ? null : profiles.get(0).getSetup();
        return TacticRequest.builder().formation(formationId).presetId(presetId).slots(new ArrayList<>(slots)).setup(setup).build();
    }

    String label(String formationId) {
        return formationRegistry.get(formationId).displayName();
    }

    Eval evaluate(List<FitPlayer> players, List<IntentProfile> slots) {
        int playerCount = players.size();
        int slotCount = slots.size();
        double[][] combined = new double[playerCount][slotCount];
        double[][] roleScores = new double[playerCount][slotCount];
        double[][] intentScores = new double[playerCount][slotCount];
        for (int p = 0; p < playerCount; p++) {
            for (int s = 0; s < slotCount; s++) {
                IntentProfile slot = slots.get(s);
                RoleFitResponse roleFit = roleFitRouter.forCareer(players.get(p), slot.getResolvedRole(), slot, slot.getSlotPosition());
                IntentFitResponse intentFit = slot.hasTags() ? intentFitService.calculate(players.get(p), slot) : null;
                roleScores[p][s] = roleFit.getScore();
                intentScores[p][s] = Objects.isNull(intentFit) ? 0 : intentFit.getScore();
                combined[p][s] = slot.hasTags() ? squadFitService.combine(roleFit, intentFit, slot) : roleFit.getScore();
            }
        }
        int[] assignment = hungarianSolver.maximize(combined);
        double combinedSum = 0;
        double roleSum = 0;
        double intentSum = 0;
        int intentCount = 0;
        int assigned = 0;
        for (int p = 0; p < playerCount; p++) {
            int s = assignment[p];
            if (s < 0) {
                continue;
            }
            assigned++;
            combinedSum += combined[p][s];
            roleSum += roleScores[p][s];
            if (slots.get(s).hasTags()) {
                intentSum += intentScores[p][s];
                intentCount++;
            }
        }
        double divisor = Math.max(assigned, 1);
        return new Eval(combinedSum / divisor, roleSum / divisor, intentCount == 0 ? null : intentSum / intentCount,
                depth(roleScores, assignment, slotCount));
    }

    private double depth(double[][] roleScores, int[] assignment, int slotCount) {
        double total = 0;
        for (int s = 0; s < slotCount; s++) {
            double bestBackup = 0;
            for (int p = 0; p < assignment.length; p++) {
                if (assignment[p] < 0) {
                    bestBackup = Math.max(bestBackup, roleScores[p][s]);
                }
            }
            total += bestBackup;
        }
        return total / Math.max(slotCount, 1) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    record Eval(double score, double squadFit, Double intentFit, double depth) {
    }

    record Oriented(Scored scored, Eval eval, boolean mirrored) {
    }

    /** Taktiği olduğu gibi ve sağ-sol aynalanmış haliyle değerlendirir, kadroya uygun olanı seçer. */
    Oriented orient(List<FitPlayer> players, Scored scored) {
        Eval original = evaluate(players, scored.profiles());
        if (!TacticMirror.isMirrorable(scored.profiles())) {
            return new Oriented(scored, original, false);
        }
        List<IntentProfile> flipped = TacticMirror.mirror(scored.profiles());
        Eval mirroredEval = evaluate(players, flipped);
        if (mirroredEval.score() <= original.score() + MIRROR_MARGIN) {
            return new Oriented(scored, original, false);
        }
        Candidate source = scored.candidate();
        Candidate candidate = Candidate.builder().kind(source.getKind()).presetId(source.getPresetId()).name(source.getName())
                .formation(source.getFormation()).formationLabel(source.getFormationLabel()).score(round1(mirroredEval.score()))
                .squadFit(round1(mirroredEval.squadFit()))
                .intentFit(Objects.isNull(mirroredEval.intentFit()) ? null : round1(mirroredEval.intentFit()))
                .tactic(toTactic(source.getFormation(), source.getTactic().getPresetId(), flipped)).build();
        return new Oriented(new Scored(candidate, flipped), mirroredEval, true);
    }

    record Scored(Candidate candidate, List<IntentProfile> profiles) {
    }
}
