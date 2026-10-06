package com.fcareer.advisor.service;

import com.fcareer.advisor.configuration.RecommenderProperties;
import com.fcareer.advisor.model.request.TacticRecommendRequest;
import com.fcareer.advisor.model.response.TacticRecommendation.Combo;
import com.fcareer.advisor.model.response.TacticRecommendation.RankedItem;
import com.fcareer.advisor.model.response.TacticRecommendation.Result;
import com.fcareer.advisor.service.AdvisorService.Eval;
import com.fcareer.advisor.service.AdvisorService.Scored;
import com.fcareer.advisor.service.FeasibilityService.Feasibility;
import com.fcareer.advisor.service.FeasibilityService.SquadProfile;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.roles.service.BehaviorTagService;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.PresetService;
import com.fcareer.tactics.service.TacticRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TacticRecommenderService {

    private static final String RECOMMEND_TACTIC = "RECOMMEND_TACTIC";
    private static final String FORMATION = "formation";
    private static final String PRESET = "preset";
    private static final Set<String> PRESET_KINDS = Set.of("STYLE", "PERSONAL");
    private static final int MIN_SQUAD = 11;
    private static final int WEAK_LINKS_SHOWN = 2;
    private static final int PERCENT = 100;
    private static final String DIFFERENT_FORMATION = "differentFormation";
    private static final String DIFFERENT_PRESET = "differentPreset";

    private final CareerService careerService;
    private final PlayerService playerService;
    private final FormationRegistry formationRegistry;
    private final PresetService presetService;
    private final BehaviorTagService behaviorTagService;
    private final AdvisorService advisorService;
    private final PositionAdviceService positionAdviceService;
    private final FeasibilityService feasibilityService;
    private final SquadFitService squadFitService;
    private final TacticRuleService tacticRuleService;
    private final RecommenderProperties properties;
    private final CreditGuard creditGuard;
    private final BillingProperties billingProperties;

    public CreditedResponse<Result> recommend(Long userId, TacticRecommendRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        if (squad.playerIds().size() < MIN_SQUAD) {
            log.warn("Squad too small for tactic recommender careerId : {} size : {}", request.getCareerId(), squad.playerIds().size());
            throw new InvalidTacticException("squad size");
        }
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId());
        return creditGuard.runCharged(userId, billingProperties.getAdvisorCost(), RECOMMEND_TACTIC, () -> compute(players, request));
    }

    private Result compute(List<FitPlayer> players, TacticRecommendRequest request) {
        List<PresetResponse> presets = presetService.findByKind(null).stream().filter(p -> PRESET_KINDS.contains(p.getKind())).toList();
        List<FormationDefinition> formations = formationRegistry.findAll();
        validateLocks(request, presets, formations);

        SquadProfile profile = feasibilityService.profile(players, positionAdviceService.bestScores(players));
        Map<String, FitTag> tagCatalog = behaviorTagService.fitTagsById();
        List<FormationDefinition> searchFormations = searchFormations(players, profile, formations, request);
        List<PresetResponse> searchPresets = Objects.isNull(request.getLockPreset()) ? presets
                : presets.stream().filter(p -> p.getId().equals(request.getLockPreset())).toList();

        List<Ranked> ranked = new ArrayList<>();
        for (PresetResponse preset : searchPresets) {
            Map<String, List<String>> template = advisorService.styleTemplate(preset, tagCatalog);
            for (FormationDefinition formation : searchFormations) {
                Feasibility feasibility = feasibilityService.evaluate(players, profile, formation, preset.getId());
                Scored scored = advisorService.scoreStyle(players, preset, formation, template);
                AdvisorService.Oriented oriented = advisorService.orient(players, scored);
                ranked.add(new Ranked(preset, formation, oriented.scored(), oriented.eval(), feasibility,
                        combine(oriented.eval(), feasibility), oriented.mirrored()));
            }
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());

        return Result.builder()
                .formations(marginal(ranked, true, request, profile, players))
                .presets(marginal(ranked, false, request, profile, players))
                .bestCombos(bestCombos(players, ranked, request))
                .evaluated(ranked.size())
                .build();
    }

    private double combine(Eval eval, Feasibility feasibility) {
        double intent = Objects.isNull(eval.intentFit()) ? eval.squadFit() : eval.intentFit();
        double raw = properties.getSquadFitWeight() * eval.squadFit() + properties.getIntentFitWeight() * intent
                + properties.getDepthWeight() * eval.depth() * PERCENT;
        return raw * feasibility.multiplier();
    }

    private List<FormationDefinition> searchFormations(List<FitPlayer> players, SquadProfile profile, List<FormationDefinition> formations,
                                                       TacticRecommendRequest request) {
        if (Objects.nonNull(request.getLockFormation())) {
            return formations.stream().filter(f -> f.id().equals(request.getLockFormation())).toList();
        }
        if (Objects.nonNull(request.getLockPreset())) {
            return formations;
        }
        Map<String, Double> base = new HashMap<>();
        for (FormationDefinition formation : formations) {
            Eval eval = advisorService.evaluate(players, advisorService.resolve(formation.id(), null, Map.of()));
            base.put(formation.id(), eval.squadFit() * feasibilityService.formationOnly(players, profile, formation).multiplier());
        }
        return formations.stream().sorted(Comparator.comparingDouble((FormationDefinition f) -> base.get(f.id())).reversed())
                .limit(properties.getTopFormations()).toList();
    }

    private List<RankedItem> marginal(List<Ranked> ranked, boolean byFormation, TacticRecommendRequest request, SquadProfile profile,
                                      List<FitPlayer> players) {
        Map<String, Ranked> best = new LinkedHashMap<>();
        for (Ranked item : ranked) {
            String key = byFormation ? item.formation().id() : item.preset().getId();
            best.putIfAbsent(key, item);
        }
        return best.values().stream().limit(properties.getListSize()).map(item -> {
            Feasibility feasibility = byFormation ? feasibilityService.formationOnly(players, profile, item.formation()) : item.feasibility();
            String note = feasibility.notes().isEmpty() ? null : feasibility.notes().get(0);
            return RankedItem.builder()
                    .id(byFormation ? item.formation().id() : item.preset().getId())
                    .label(byFormation ? advisorService.label(item.formation().id()) : item.preset().getName())
                    .pct(pct(item.score())).note(note).build();
        }).toList();
    }

    private List<Combo> bestCombos(List<FitPlayer> players, List<Ranked> ranked, TacticRecommendRequest request) {
        List<Ranked> picked = new ArrayList<>();
        for (Ranked candidate : ranked) {
            if (picked.size() >= request.getTopN()) {
                break;
            }
            if (picked.isEmpty() || isDifferentEnough(picked.get(0), candidate, request.getDiversityMode())) {
                if (picked.stream().noneMatch(p -> sameCombo(p, candidate))) {
                    picked.add(candidate);
                }
            }
        }
        return picked.stream().map(item -> toCombo(players, item, ranked)).toList();
    }

    private boolean isDifferentEnough(Ranked first, Ranked candidate, String mode) {
        boolean differentFormation = !first.formation().id().equals(candidate.formation().id());
        boolean differentPreset = !first.preset().getId().equals(candidate.preset().getId());
        return switch (mode) {
            case DIFFERENT_FORMATION -> differentFormation;
            case DIFFERENT_PRESET -> differentPreset;
            default -> differentFormation || differentPreset;
        };
    }

    private boolean sameCombo(Ranked a, Ranked b) {
        return a.formation().id().equals(b.formation().id()) && a.preset().getId().equals(b.preset().getId());
    }

    private Combo toCombo(List<FitPlayer> players, Ranked item, List<Ranked> all) {
        SquadFitResponse lineup = squadFitService.solve(players, item.scored().profiles(), tacticRuleService.enabledRules());
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format(Locale.ROOT, "Kadro uyumu %%%.0f%s, yedek derinliği %%%.0f.", item.eval().squadFit(),
                Objects.isNull(item.eval().intentFit()) ? "" : String.format(Locale.ROOT, ", niyet uyumu %%%.0f", item.eval().intentFit()),
                item.eval().depth() * PERCENT));
        if (item.mirrored()) {
            reasons.add("Taktik kadronuza göre sağ-sol aynalandı: görevler, bu kanatta daha uygun oyuncularınıza kaydırıldı.");
        }
        reasons.addAll(item.feasibility().notes());
        lowerFormationNote(all, item).ifPresent(reasons::add);
        lineup.getWeakLinks().stream().limit(WEAK_LINKS_SHOWN).forEach(w -> reasons.add("Zayıf halka: " + w.getSentence()));
        return Combo.builder().formation(item.formation().id()).formationLabel(advisorService.label(item.formation().id()))
                .presetId(item.preset().getId()).presetName(item.preset().getName()).pct(pct(item.score()))
                .squadFit(round1(item.eval().squadFit())).intentFit(Objects.isNull(item.eval().intentFit()) ? null : round1(item.eval().intentFit()))
                .feasibility((int) Math.round(item.feasibility().multiplier() * PERCENT)).mirrored(item.mirrored()).reasons(reasons)
                .weakLinks(lineup.getWeakLinks()).tactic(item.scored().candidate().getTactic()).lineup(lineup).build();
    }

    private java.util.Optional<String> lowerFormationNote(List<Ranked> all, Ranked chosen) {
        return all.stream().filter(r -> r.preset().getId().equals(chosen.preset().getId()) && !r.formation().id().equals(chosen.formation().id())
                        && !r.feasibility().notes().isEmpty() && r.feasibility().multiplier() < 1.0)
                .findFirst().map(r -> advisorService.label(r.formation().id()) + " düşük: " + r.feasibility().notes().get(0));
    }

    private void validateLocks(TacticRecommendRequest request, List<PresetResponse> presets, List<FormationDefinition> formations) {
        if (Objects.nonNull(request.getLockFormation()) && formations.stream().noneMatch(f -> f.id().equals(request.getLockFormation()))) {
            throw new NotFoundException(FORMATION, request.getLockFormation());
        }
        if (Objects.nonNull(request.getLockPreset()) && presets.stream().noneMatch(p -> p.getId().equals(request.getLockPreset()))) {
            throw new NotFoundException(PRESET, request.getLockPreset());
        }
    }

    private static int pct(double score) {
        return (int) Math.max(0, Math.min(PERCENT, Math.round(score)));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record Ranked(PresetResponse preset, FormationDefinition formation, Scored scored, Eval eval, Feasibility feasibility, double score,
                          boolean mirrored) {
    }
}
