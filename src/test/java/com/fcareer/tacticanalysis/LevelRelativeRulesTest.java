package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-7b: seviyeye göreli eşikler. counter_risk (stoper hızı), buildup_mismatch (pas güvenliği), no_box_target (hava hedefi) mutlak sayıyla değil bağlam referansıyla (kariyerde ligin aynı mevki medyanı,
 * kariyer dışında ilk 11 OVR bandının katalog medyanı) karşılaştırılır: eşik = referans − marj. Dayanıklılık mutlak kalır. Referans yoksa mutlak eşik.
 */
class LevelRelativeRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RoleMovements movements = new RoleMovements(MAPPER);
    private final TacticRules rules = new TacticRules(MAPPER);
    private final MetricsCalculator calculator = new MetricsCalculator();
    private final TacticAnalyzer analyzer = new TacticAnalyzer(movements, rules, calculator, new RuleEngine(rules, new RuleMetrics(calculator)));

    private static final Map<String, Double> LEAGUE_TWO = Map.of("CB_PACE_REF", 58.0, "BUILDUP_SAFETY_REF", 56.0, "AERIAL_REF", 56.0);
    private static final Map<String, Double> PREMIER_LEAGUE = Map.of("CB_PACE_REF", 72.0, "BUILDUP_SAFETY_REF", 72.0, "AERIAL_REF", 70.0);

    private static List<Slot> slots() {
        String[][] base = {{"GK", "GK", "50", "95", "goalkeeper_defend"}, {"LB", "LB", "12", "75", "fullback_balanced"}, {"LCB", "CB", "37", "80", "defender_defend"}, {"RCB", "CB", "63", "80", "defender_defend"},
                {"RB", "RB", "88", "75", "fullback_balanced"}, {"LDM", "CDM", "38", "60", "holding_defend"}, {"RDM", "CDM", "62", "60", "holding_defend"}, {"LM", "LM", "10", "45", "winger_balanced"},
                {"CAM", "CAM", "50", "34", "playmaker_balanced"}, {"RM", "RM", "90", "45", "winger_balanced"}, {"ST", "ST", "50", "14", "advanced_forward_attack"}};
        List<Slot> out = new ArrayList<>();
        for (String[] s : base) {
            out.add(new Slot(s[0], s[1], Integer.parseInt(s[2]), Integer.parseInt(s[3]), s[4]));
        }
        return out;
    }

    private static Map<String, FitPlayer> everyone(int attrValue) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, attrValue);
        }
        Map<String, FitPlayer> players = new HashMap<>();
        for (Slot s : slots()) {
            players.put(s.slotId(), FitPlayer.builder().id(1L).name("P").attrs(attrs).positions(List.of()).playstyles(Map.of()).heightCm(180).preferredFoot("Right").build());
        }
        return players;
    }

    private Analysis run(BuildUpType buildUp, int depth, int attrValue, Map<String, Double> refs, String label) {
        return analyzer.analyze(new Input("t", buildUp, depth, slots(), everyone(attrValue), refs, label));
    }

    private static Optional<Finding> finding(Analysis a, String code) {
        return a.findings().stream().filter((f) -> f.code().equals(code)).findFirst();
    }

    @Test
    void theSameRelativeProfileGivesTheSameCounterRiskInLeagueTwoAndInThePremierLeague() {
        // her bağlamda stoper hızı referansın 6 altında (marj 3'ten fazla): iki bağlamda da tetiklenir
        Finding l2 = finding(run(BuildUpType.BALANCED, 80, 52, LEAGUE_TWO, "EFL League Two"), "counter_risk").orElseThrow();
        Finding pl = finding(run(BuildUpType.BALANCED, 80, 66, PREMIER_LEAGUE, "Premier League"), "counter_risk").orElseThrow();
        assertThat(l2.severity()).isEqualTo(pl.severity());
        // ve referansın 2 üstünde/altında (marj içinde): ikisinde de tetiklenmez
        assertThat(finding(run(BuildUpType.BALANCED, 80, 56, LEAGUE_TWO, "EFL League Two"), "counter_risk")).isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 80, 70, PREMIER_LEAGUE, "Premier League"), "counter_risk")).isEmpty();
    }

    @Test
    void aLeagueTwoCentreBackWhoIsFastForHisLeagueDoesNotTriggerCounterRisk_butTheSamePaceDoesInThePremierLeague() {
        Analysis inLeagueTwo = run(BuildUpType.BALANCED, 80, 62, LEAGUE_TWO, "EFL League Two");
        assertThat(finding(inLeagueTwo, "counter_risk")).as("62 > ligin CB medyanı 58 − 3: mutlak eşik (66) tetiklerdi").isEmpty();
        Finding inPremier = finding(run(BuildUpType.BALANCED, 80, 62, PREMIER_LEAGUE, "Premier League"), "counter_risk").orElseThrow();
        assertThat(inPremier.evidence().get(1).value()).isEqualTo(62.0);
    }

    @Test
    void relativeEvidenceShowsTheValueTheReferenceAndTheMargin() {
        Finding f = finding(run(BuildUpType.BALANCED, 80, 52, LEAGUE_TWO, "EFL League Two"), "counter_risk").orElseThrow();
        TacticAnalysisModels.Evidence e = f.evidence().get(1);
        assertThat(e.metric()).isEqualTo("CB_PACE_AVG");
        assertThat(e.value()).isEqualTo(52.0);
        assertThat(e.reference()).isEqualTo(58.0);
        assertThat(e.margin()).isEqualTo(3.0);
        assertThat(e.threshold()).isEqualTo(55.0);
        assertThat(f.text()).contains("52").contains("58").contains("3").contains("EFL League Two");
    }

    @Test
    void buildUpMismatchIsRelativeToThePassersOfTheLevel() {
        assertThat(finding(run(BuildUpType.SHORT, 55, 58, LEAGUE_TWO, "EFL League Two"), "buildup_mismatch")).as("58 ≥ 56 − 3: ligine göre yeterli").isEmpty();
        assertThat(finding(run(BuildUpType.SHORT, 55, 58, PREMIER_LEAGUE, "Premier League"), "buildup_mismatch")).as("58 < 72 − 3").isPresent();
        assertThat(finding(run(BuildUpType.BALANCED, 55, 40, PREMIER_LEAGUE, "Premier League"), "buildup_mismatch")).as("Short seçili değil").isEmpty();
    }

    @Test
    void boxTargetIsRelativeToTheAerialTargetsOfTheLevel() {
        // aerial = (2·değer + boy skoru 50) / 3
        assertThat(finding(run(BuildUpType.BALANCED, 55, 58, LEAGUE_TWO, "EFL League Two"), "no_box_target")).as("aerial 55,3 ≥ 56 − 3").isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 55, 58, PREMIER_LEAGUE, "Premier League"), "no_box_target")).as("aerial 55,3 < 70 − 3").isPresent();
    }

    @Test
    void withoutReferencesTheAbsoluteThresholdsStillApply() {
        assertThat(finding(run(BuildUpType.BALANCED, 80, 60, Map.of(), null), "counter_risk")).isPresent();
        assertThat(finding(run(BuildUpType.BALANCED, 80, 80, Map.of(), null), "counter_risk")).isEmpty();
        TacticAnalysisModels.Evidence e = finding(run(BuildUpType.BALANCED, 80, 60, Map.of(), null), "counter_risk").orElseThrow().evidence().get(1);
        assertThat(e.reference()).isNull();
    }

    @Test
    void staminaStaysAbsoluteBecauseFatigueIsAbsolute() {
        Analysis a = run(BuildUpType.BALANCED, 80, 45, PREMIER_LEAGUE, "Premier League");
        TacticAnalysisModels.Evidence e = finding(a, "stamina_gap").orElseThrow().evidence().get(0);
        assertThat(e.reference()).isNull();
        assertThat(e.margin()).isNull();
    }

    @Test
    void theAnalysisRemembersWhichLevelItWasMeasuredAgainst() {
        assertThat(run(BuildUpType.BALANCED, 55, 70, LEAGUE_TWO, "EFL League Two").levelLabel()).isEqualTo("EFL League Two");
    }
}
