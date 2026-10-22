package com.fcareer.advisor.service;

import com.fcareer.advisor.service.AdvisorService.Eval;
import com.fcareer.advisor.service.FeasibilityService.SquadProfile;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tactics.model.FormationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Formasyon-only sıralama (preset yok): skor = varsayılan rollerle kadro uyumu × formasyon uygulanabilirliği, yüksekten düşüğe.
 * Taktik önerici (ücretli /advisor/tactic, ilk N formasyonu tarar) ve ücretsiz başlangıç önerisi (starter-tactic, birinciyi alır) aynı sıralamayı kullanır.
 */
@Component
@RequiredArgsConstructor
public class FormationRanker {

    public record RankedFormation(FormationDefinition formation, double score) {
    }

    private final AdvisorService advisorService;
    private final FeasibilityService feasibilityService;

    public List<RankedFormation> rank(List<FitPlayer> players, SquadProfile profile, List<FormationDefinition> formations) {
        return formations.stream()
                .map(formation -> {
                    Eval eval = advisorService.evaluate(players, advisorService.resolve(formation.id(), null, Map.of()));
                    return new RankedFormation(formation, eval.squadFit() * feasibilityService.formationOnly(players, profile, formation).multiplier());
                })
                .sorted(Comparator.comparingDouble(RankedFormation::score).reversed())
                .toList();
    }
}
