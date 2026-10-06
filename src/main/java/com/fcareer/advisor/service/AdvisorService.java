package com.fcareer.advisor.service;

import com.fcareer.advisor.model.request.AdvisorRequest;
import com.fcareer.advisor.model.response.AdvisorResponses.Advice;
import com.fcareer.advisor.model.response.AdvisorResponses.Candidate;
import com.fcareer.advisor.model.response.AdvisorResponses.TransferSuggestion;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.model.response.WeakLink;
import com.fcareer.fitengine.service.HungarianSolver;
import com.fcareer.fitengine.service.IntentFitService;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.fitengine.service.TacticMirror;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.service.RecommendationService;
import com.fcareer.roles.service.BehaviorTagService;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.PresetService;
import com.fcareer.tactics.service.TacticResolver;
import com.fcareer.tactics.service.TacticRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorService {

    private static final String ADVISOR = "ADVISOR";
    private static final double MIRROR_MARGIN = 0.5;
    private static final String STYLE = "STYLE";
    private static final String REPLICA = "REPLICA";
    private static final String PERSONAL = "PERSONAL";
    private static final String ADAPTIVE = "ADAPTIVE";
    private static final String CUSTOM_NAME = "Kadroya özel kurulum";
    private static final String BETTER_ELSEWHERE = "BETTER_ELSEWHERE";
    private static final String PHILOSOPHY_OK = "PHILOSOPHY_OK";
    private static final String BEST_FIT = "BEST_FIT";
    private static final int MIN_SQUAD = 11;
    private static final int TOP_FORMATIONS = 8;
    private static final int ALTERNATIVES = 5;
    private static final int TRANSFER_SLOTS = 3;
    private static final int TRANSFER_ITEMS = 3;
    private static final double STYLE_TOLERANCE = 3.0;
    private static final double TOLERANCE_PER_WEIGHT = 0.1;

    private final CareerService careerService;
    private final PlayerService playerService;
    private final TacticResolver tacticResolver;
    private final TacticRuleService tacticRuleService;
    private final RoleService roleService;
    private final BehaviorTagService behaviorTagService;
    private final FormationRegistry formationRegistry;
    private final PresetService presetService;
    private final RoleFitService roleFitService;
    private final IntentFitService intentFitService;
    private final SquadFitService squadFitService;
    private final HungarianSolver hungarianSolver;
    private final RecommendationService recommendationService;
    private final PositionAdviceService positionAdviceService;
    private final CreditGuard creditGuard;
    private final BillingProperties billingProperties;

    public CreditedResponse<Advice> advise(Long userId, AdvisorRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        if (squad.playerIds().size() < MIN_SQUAD) {
            log.warn("Squad too small for advisor careerId : {} size : {}", request.getCareerId(), squad.playerIds().size());
            throw new InvalidTacticException("squad size");
        }
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId());
        return creditGuard.runCharged(userId, billingProperties.getAdvisorCost(), ADVISOR, () -> compute(players, request, squad));
    }

    private Advice compute(List<FitPlayer> players, AdvisorRequest request, CareerService.CareerSquad squad) {
        Map<String, FitTag> tagCatalog = behaviorTagService.fitTagsById();
        List<PresetResponse> presets = presetService.findByKind(null);
        List<PresetResponse> styles = presets.stream().filter(p -> STYLE.equals(p.getKind()) || PERSONAL.equals(p.getKind())).toList();
        List<PresetResponse> replicas = presets.stream().filter(p -> REPLICA.equals(p.getKind())).toList();
        PresetResponse philosophy = presets.stream().filter(p -> p.getId().equals(request.getPhilosophyPresetId())).findFirst().orElse(null);

        List<Scored> all = new ArrayList<>();
        Map<String, Eval> formationBase = new LinkedHashMap<>();
        for (FormationDefinition formation : formationRegistry.findAll()) {
            List<IntentProfile> profiles = resolve(formation.id(), null, Map.of());
            formationBase.put(formation.id(), evaluate(players, profiles));
        }
        List<String> topFormations = formationBase.entrySet().stream()
                .sorted(Map.Entry.<String, Eval>comparingByValue(Comparator.comparingDouble(Eval::squadFit)).reversed())
                .limit(TOP_FORMATIONS).map(Map.Entry::getKey).toList();

        for (PresetResponse style : styles) {
            Map<String, List<String>> template = styleTemplate(style, tagCatalog);
            boolean isPhilosophy = Objects.nonNull(philosophy) && philosophy.getId().equals(style.getId());
            for (FormationDefinition formation : formationRegistry.findAll()) {
                if (isPhilosophy || topFormations.contains(formation.id())) {
                    all.add(scoreStyle(players, style, formation, template));
                }
            }
        }
        for (PresetResponse replica : replicas) {
            List<IntentProfile> profiles = resolve(replica.getFormation(), replica.getId(), Map.of());
            all.add(scored(REPLICA, replica, replica.getFormation(), profiles, evaluate(players, profiles)));
        }
        for (String formationId : topFormations) {
            all.add(adaptive(players, formationId));
        }

        return buildAdvice(players, request, squad, all, philosophy);
    }

    Scored scoreStyle(List<FitPlayer> players, PresetResponse style, FormationDefinition formation, Map<String, List<String>> template) {
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
        List<IntentProfile> profiles = resolve(formation.id(), null, bySlot);
        return scored(style.getKind().equals(PERSONAL) ? STYLE : style.getKind(), style, formation.id(), profiles, evaluate(players, profiles));
    }

    private Scored adaptive(List<FitPlayer> players, String formationId) {
        List<IntentProfile> base = resolve(formationId, null, Map.of());
        int[] assignment = assign(players, base);
        Map<String, FitRole> roles = roleService.fitRolesById();
        List<IntentProfile> tailored = new ArrayList<>();
        for (int s = 0; s < base.size(); s++) {
            IntentProfile slot = base.get(s);
            FitPlayer holder = holderOf(players, assignment, s);
            FitRole best = slot.getResolvedRole();
            double bestScore = Double.NEGATIVE_INFINITY;
            for (FitRole role : roles.values()) {
                if (Objects.isNull(holder) || !role.getPositions().contains(slot.getSlotPosition())) {
                    continue;
                }
                double score = roleFitService.calculate(holder, role, null, slot.getSlotPosition()).getScore();
                if (score > bestScore) {
                    bestScore = score;
                    best = role;
                }
            }
            tailored.add(tacticResolver.slotIntent(slot.getSlotId(), slot.getSlotPosition(), slot.getSlotGroup(), best.getId(), List.of()));
        }
        Eval eval = evaluate(players, tailored);
        Candidate candidate = Candidate.builder().kind(ADAPTIVE).name(CUSTOM_NAME).formation(formationId)
                .formationLabel(label(formationId)).score(round1(eval.score())).squadFit(round1(eval.squadFit()))
                .intentFit(null).tactic(toTactic(formationId, null, tailored)).build();
        return new Scored(candidate, tailored);
    }

    private Advice buildAdvice(List<FitPlayer> players, AdvisorRequest request, CareerService.CareerSquad squad, List<Scored> all,
                               PresetResponse philosophy) {
        Scored bestNamed = all.stream().filter(s -> !ADAPTIVE.equals(s.candidate().getKind()))
                .max(Comparator.comparingDouble(s -> s.candidate().getScore())).orElseThrow();
        Scored bestCustom = all.stream().filter(s -> ADAPTIVE.equals(s.candidate().getKind()))
                .max(Comparator.comparingDouble(s -> s.candidate().getScore())).orElseThrow();
        Scored recommended = bestNamed.candidate().getScore() >= bestCustom.candidate().getScore() - STYLE_TOLERANCE ? bestNamed : bestCustom;
        Scored philosophyBest = Objects.isNull(philosophy) ? null : all.stream()
                .filter(s -> philosophy.getId().equals(s.candidate().getPresetId()))
                .max(Comparator.comparingDouble(s -> s.candidate().getScore())).orElse(null);

        List<String> reasons = new ArrayList<>();
        String verdict = BEST_FIT;
        double tolerance = request.getPhilosophyWeight() * TOLERANCE_PER_WEIGHT;
        if (Objects.nonNull(philosophyBest)) {
            double gap = recommended.candidate().getScore() - philosophyBest.candidate().getScore();
            if (philosophyBest == recommended || gap <= tolerance) {
                verdict = PHILOSOPHY_OK;
                if (philosophyBest != recommended) {
                    reasons.add(String.format(Locale.ROOT, "Anlayışın (%s) bu kadroyla en iyi seçenekten sadece %.1f puan geride; oyun anlayışına sadık kalman mantıklı.",
                            philosophy.getName(), gap));
                } else {
                    reasons.add(String.format(Locale.ROOT, "Anlayışın (%s) bu kadroyla zaten en iyi seçeneklerden biri.", philosophy.getName()));
                }
                recommended = philosophyBest;
            } else {
                verdict = BETTER_ELSEWHERE;
                reasons.add(String.format(Locale.ROOT, "Anlayışın (%s) bu kadroyla en iyi %s formasyonunda %.1f puan; önerilen kurulum %.1f puan ile %.1f puan önde. Bu fark seçtiğin sadakat payını (%.1f) aşıyor.",
                        philosophy.getName(), philosophyBest.candidate().getFormationLabel(), philosophyBest.candidate().getScore(),
                        recommended.candidate().getScore(), gap, tolerance));
            }
        }

        Candidate recommendedCandidate = withLineup(players, recommended);
        Candidate philosophyCandidate = Objects.isNull(philosophyBest) || philosophyBest == recommended ? null : withLineup(players, philosophyBest);
        Candidate customCandidate = bestCustom == recommended ? null : withLineup(players, bestCustom);

        SquadFitResponse lineup = recommendedCandidate.getLineup();
        reasons.add(String.format(Locale.ROOT, "%s / %s: kadro uyumu %.1f.", recommendedCandidate.getName(), recommendedCandidate.getFormationLabel(),
                recommendedCandidate.getSquadFit()));
        lineup.getWeakLinks().stream().findFirst().ifPresent(w -> reasons.add("En zayıf halka: " + w.getSentence()));
        if (!lineup.getWeakLinks().isEmpty() && !request.isAllowTransfers()) {
            reasons.add("Transfer yapamıyorsan zayıf slotlar için aşağıdaki mevki önerilerine bak: oyuncuların listelenmeyen ama daha iyi oynayabileceği mevkiler var.");
        }
        reasons.add(all.size() + " kurulum (formasyon × oyun anlayışı) denendi.");

        Advice advice = Advice.builder()
                .recommended(recommendedCandidate).verdict(verdict).reasons(reasons)
                .philosophy(philosophyCandidate).bestCustom(customCandidate)
                .alternatives(alternatives(all, recommended))
                .playerPositions(positionAdviceService.advise(players))
                .transferSuggestions(request.isAllowTransfers() ? transferSuggestions(recommendedCandidate, squad) : null)
                .evaluated(all.size()).build();
        return advice;
    }

    private Candidate withLineup(List<FitPlayer> players, Scored scored) {
        SquadFitResponse lineup = squadFitService.solve(players, scored.profiles(), tacticRuleService.enabledRules());
        Candidate source = scored.candidate();
        return Candidate.builder().kind(source.getKind()).presetId(source.getPresetId()).name(source.getName())
                .formation(source.getFormation()).formationLabel(source.getFormationLabel()).score(source.getScore())
                .squadFit(source.getSquadFit()).intentFit(source.getIntentFit()).tactic(source.getTactic()).lineup(lineup).build();
    }

    private List<Candidate> alternatives(List<Scored> all, Scored recommended) {
        Map<String, Scored> bestPerKey = new LinkedHashMap<>();
        all.stream().sorted(Comparator.comparingDouble((Scored s) -> s.candidate().getScore()).reversed()).forEach(s -> {
            String key = s.candidate().getKind() + ":" + Objects.toString(s.candidate().getPresetId(), "");
            bestPerKey.putIfAbsent(key, s);
        });
        return bestPerKey.values().stream().filter(s -> s != recommended).limit(ALTERNATIVES).map(Scored::candidate).toList();
    }

    private List<TransferSuggestion> transferSuggestions(Candidate recommended, CareerService.CareerSquad squad) {
        SquadFitResponse lineup = recommended.getLineup();
        List<TransferSuggestion> suggestions = new ArrayList<>();
        for (WeakLink weak : lineup.getWeakLinks().stream().limit(TRANSFER_SLOTS).toList()) {
            lineup.getSlots().stream().filter(s -> s.getSlotId().equals(weak.getSlotId())).findFirst().ifPresent(slot ->
                    suggestions.add(TransferSuggestion.builder().slotId(slot.getSlotId()).position(slot.getPosition()).roleId(slot.getRoleId())
                            .items(recommendationService.preview(RecommendSlotRequest.builder().roleId(slot.getRoleId())
                                    .position(slot.getPosition()).tags(slot.getTags()).gender(squad.gender()).limit(TRANSFER_ITEMS).build())
                                    .getItems()).build()));
        }
        return suggestions;
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

    List<IntentProfile> resolve(String formationId, String presetId, Map<String, List<String>> tagsBySlot) {
        List<SlotSelection> selections = tagsBySlot.entrySet().stream()
                .map(e -> SlotSelection.builder().slotId(e.getKey()).tags(e.getValue()).build()).toList();
        return tacticResolver.resolve(TacticRequest.builder().formation(formationId).presetId(presetId).slots(new ArrayList<>(selections)).build()).slots();
    }

    TacticRequest toTactic(String formationId, String presetId, List<IntentProfile> profiles) {
        List<SlotSelection> slots = profiles.stream().map(p -> SlotSelection.builder().slotId(p.getSlotId())
                .tags(p.getTags().stream().map(FitTag::getId).toList()).roleId(p.getResolvedRole().getId()).build()).toList();
        return TacticRequest.builder().formation(formationId).presetId(presetId).slots(new ArrayList<>(slots)).build();
    }

    String label(String formationId) {
        return Objects.requireNonNullElse(formationRegistry.get(formationId).label(), formationId);
    }

    private int[] assign(List<FitPlayer> players, List<IntentProfile> slots) {
        double[][] combined = new double[players.size()][slots.size()];
        for (int p = 0; p < players.size(); p++) {
            for (int s = 0; s < slots.size(); s++) {
                IntentProfile slot = slots.get(s);
                combined[p][s] = roleFitService.calculate(players.get(p), slot.getResolvedRole(), slot, slot.getSlotPosition()).getScore();
            }
        }
        return hungarianSolver.maximize(combined);
    }

    private FitPlayer holderOf(List<FitPlayer> players, int[] playerToSlot, int slot) {
        for (int p = 0; p < playerToSlot.length; p++) {
            if (playerToSlot[p] == slot) {
                return players.get(p);
            }
        }
        return null;
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
                RoleFitResponse roleFit = roleFitService.calculate(players.get(p), slot.getResolvedRole(), slot, slot.getSlotPosition());
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
