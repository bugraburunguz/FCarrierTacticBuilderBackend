package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Severity;
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
 * FIT-2 madde 7 (fit_engine_spec §5): kontra riski (bindiren iki bek + yavaş stoper + tek pivot), yaratıcılık yetersiz (≥3 yaratıcı rol + düşük vizyon),
 * santrfor yalnız (tek forvet + içe kat eden iki kanat + kutuya geç gelen yok). Her kural için tetikleyen ve tetiklemeyen fixture; T4 birebir.
 */
class TacticRulesFit2Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MetricsCalculator calculator = new MetricsCalculator();
    private final TacticRules rules = new TacticRules(MAPPER);
    private final TacticAnalyzer analyzer = new TacticAnalyzer(new RoleMovements(MAPPER), rules, calculator, new RuleEngine(rules, new RuleMetrics(calculator)));

    /** 4-3-3 (tek CDM), formations.json konumları. */
    private static List<String[]> base433() {
        return List.of(new String[]{"GK", "GK", "50", "95", "goalkeeper_defend"}, new String[]{"LB", "LB", "12", "75", "fullback_balanced"}, new String[]{"LCB", "CB", "37", "80", "defender_defend"},
                new String[]{"RCB", "CB", "63", "80", "defender_defend"}, new String[]{"RB", "RB", "88", "75", "fullback_balanced"}, new String[]{"CDM", "CDM", "50", "62", "holding_defend"},
                new String[]{"LCM", "CM", "35", "45", "box_to_box_balanced"}, new String[]{"RCM", "CM", "65", "45", "box_to_box_balanced"}, new String[]{"LW", "LW", "15", "20", "winger_balanced"},
                new String[]{"RW", "RW", "85", "20", "winger_balanced"}, new String[]{"ST", "ST", "50", "14", "advanced_forward_attack"});
    }

    private Analysis run(int depth, Map<String, FitPlayer> players, String... overrides) {
        Map<String, String> roleBySlot = new HashMap<>();
        for (String o : overrides) {
            roleBySlot.put(o.split("=")[0], o.split("=")[1]);
        }
        List<Slot> slots = new ArrayList<>();
        for (String[] s : base433()) {
            slots.add(new Slot(s[0], s[1], Integer.parseInt(s[2]), Integer.parseInt(s[3]), roleBySlot.getOrDefault(s[0], s[4])));
        }
        return analyzer.analyze(new Input("4-3-3", BuildUpType.BALANCED, depth, slots, players));
    }

    private static FitPlayer player(int all, Map<Attr, Integer> overrides) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, all);
        }
        attrs.putAll(overrides);
        return FitPlayer.builder().id(1L).name("P").attrs(attrs).positions(List.of()).playstyles(Map.of()).heightCm(182).preferredFoot("Right").build();
    }

    /** Herkes `all`; verilen slotlara özel attribute. */
    private static Map<String, FitPlayer> squad(int all, Map<String, Map<Attr, Integer>> bySlot) {
        Map<String, FitPlayer> players = new HashMap<>();
        base433().forEach((s) -> players.put(s[0], player(all, bySlot.getOrDefault(s[0], Map.of()))));
        return players;
    }

    private static Map<Attr, Integer> pace(int v) {
        return Map.of(Attr.ACCELERATION, v, Attr.SPRINT_SPEED, v);
    }

    private static Optional<Finding> finding(Analysis a, String code) {
        return a.findings().stream().filter((f) -> f.code().equals(code)).findFirst();
    }

    // ---- kontra riski (T4) ------------------------------------------------------------------------------------------------------

    private static final String[] TWO_ATTACKING_WINGBACKS = {"LB=attacking_wingback_attack", "RB=attacking_wingback_attack"};

    @Test
    void t4_counterRisk_twoAttackingWingbacksSlowCentreBacksSinglePivot() {
        Map<String, FitPlayer> players = squad(70, Map.of("LCB", pace(58), "RCB", pace(61)));
        Finding f = finding(run(50, players, TWO_ATTACKING_WINGBACKS), "counter_risk_overlap").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.RISK);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("OVERLAPPING_FULLBACK_COUNT", "CB_PACE_AVG", "ANCHOR_COUNT");
        assertThat(f.evidence().get(1).value()).isEqualTo(59.5);
        assertThat(f.recommendation().text()).as("fix önerisi").isNotBlank();
        assertThat(f.text()).contains("2").contains("59.5");
    }

    @Test
    void counterRisk_doesNotTriggerWithBalancedFullbacksFastCentreBacksOrTwoPivots() {
        Map<String, FitPlayer> slow = squad(70, Map.of("LCB", pace(58), "RCB", pace(61)));
        assertThat(finding(run(50, slow), "counter_risk_overlap")).as("bekler bindirmiyor").isEmpty();
        assertThat(finding(run(50, squad(70, Map.of("LCB", pace(75), "RCB", pace(72))), TWO_ATTACKING_WINGBACKS), "counter_risk_overlap")).as("hızlı stoper").isEmpty();
        assertThat(finding(run(50, slow, "LB=attacking_wingback_attack", "RB=attacking_wingback_attack", "LCM=holding_defend"), "counter_risk_overlap")).as("iki pivot").isEmpty();
        assertThat(run(50, Map.of(), TWO_ATTACKING_WINGBACKS).notEvaluated()).extracting((n) -> n.code()).as("oyuncusuz değerlendirilmez").contains("counter_risk_overlap");
    }

    /** Karar: yeni kontra kuralı tetiklenince eski "yüksek hat + yavaş stoper" (counter_risk) gizlenir; tek başına eski kural yine çıkar. */
    @Test
    void counterRiskOverlap_suppressesTheOldHighLineCounterRisk() {
        Map<String, FitPlayer> slow = squad(70, Map.of("LCB", pace(58), "RCB", pace(61)));
        Analysis both = run(80, slow, TWO_ATTACKING_WINGBACKS);
        assertThat(finding(both, "counter_risk_overlap")).isPresent();
        assertThat(finding(both, "counter_risk")).as("gizlenir").isEmpty();
        assertThat(finding(run(80, slow), "counter_risk")).as("bekler bindirmiyorsa eski kural kalır").isPresent();
    }

    @Test
    void counterRisk_notForThreeCentreBacks() {
        // İnceleme: 3+ stoperli hatta bekler (wingback) zaten ileri çıkar; kural dörtlü savunma içindir (FULLBACKS_DYFWD_MIN ile aynı gerekçe).
        List<Slot> slots = new ArrayList<>();
        for (String[] s : base433()) {
            String role = s[0].equals("LB") || s[0].equals("RB") ? "attacking_wingback_attack" : s[0].equals("LCM") ? "defender_defend" : s[4];
            slots.add(new Slot(s[0], s[0].equals("LCM") ? "CB" : s[1], Integer.parseInt(s[2]), s[0].equals("LCM") ? 80 : Integer.parseInt(s[3]), role));
        }
        Map<String, FitPlayer> players = squad(70, Map.of("LCB", pace(58), "RCB", pace(61), "LCM", pace(60)));
        assertThat(finding(analyzer.analyze(new Input("3-cb", BuildUpType.BALANCED, 50, slots, players)), "counter_risk_overlap")).isEmpty();
    }

    // ---- yaratıcılık yetersiz ---------------------------------------------------------------------------------------------------

    private static final String[] THREE_CREATORS = {"CDM=deep_lying_playmaker_build_up", "LCM=playmaker_balanced", "RCM=playmaker_attack"};

    @Test
    void creativity_triggersWithThreeCreativeRolesAndLowVision() {
        Map<String, FitPlayer> players = squad(70, Map.of("CDM", Map.of(Attr.VISION, 60), "LCM", Map.of(Attr.VISION, 62), "RCM", Map.of(Attr.VISION, 64)));
        Finding f = finding(run(50, players, THREE_CREATORS), "creativity_low").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.WARN);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("CREATIVE_ROLE_COUNT", "CREATIVE_VISION_AVG");
        assertThat(f.evidence().get(0).value()).isEqualTo(3);
        assertThat(f.evidence().get(1).value()).isEqualTo(62);
    }

    @Test
    void creativity_doesNotTriggerWithGoodVisionOrTwoCreators() {
        assertThat(finding(run(50, squad(75, Map.of()), THREE_CREATORS), "creativity_low")).as("vizyon iyi").isEmpty();
        assertThat(finding(run(50, squad(60, Map.of()), "LCM=playmaker_balanced", "RCM=playmaker_attack"), "creativity_low")).as("iki yaratıcı rol").isEmpty();
    }

    // ---- santrfor yalnız --------------------------------------------------------------------------------------------------------

    /** İki kanat içe kat eder; merkez orta saha kutuya koşmayan playmaker'lar. */
    private static final String[] CUTTING_WINGERS = {"LW=inside_forward_attack", "RW=inside_forward_attack", "LCM=playmaker_balanced", "RCM=playmaker_build_up"};

    @Test
    void lonelyStriker_triggersWithOneStrikerTwoCuttingWingersAndNoLateRunner() {
        Finding f = finding(run(50, Map.of(), CUTTING_WINGERS), "striker_isolated").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.WARN);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("CENTRE_FORWARD_COUNT", "WINGERS_CUTIN_MIN", "LATE_RUNNER_COUNT");
        assertThat(f.recommendation().text()).isNotBlank();
        assertThat(f.text()).as("kullanıcıya iç eğilim sayısı (0-1) gösterilmez").doesNotMatch(".*\\d\\.\\d.*");
    }

    /**
     * Karar (FIT-2 sonrası): eşik 0.35'te kalır, çünkü 0.25–0.35 bandında kutuya koşmayan roller var (classic_10_wide, half_winger_roaming/balanced,
     * playmaker_attack). Box-to-Box (her odak) ve Box Crasher açıkça geç gelen sayılır.
     */
    @Test
    void lonelyStriker_boxToBoxAndBoxCrasherCountAsLateRunnersButWideTenDoesNot() {
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack"), "striker_isolated")).as("B2B Balanced (0.25)").isEmpty();
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack", "LCM=playmaker_balanced", "RCM=box_to_box_ball_winning"), "striker_isolated"))
                .as("B2B Ball Winning (0.15) de sayılır").isEmpty();
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack", "LCM=playmaker_balanced", "RCM=playmaker_build_up", "CDM=box_crasher_balanced"), "striker_isolated"))
                .as("Box Crasher").isEmpty();
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack", "LCM=half_winger_roaming", "RCM=playmaker_attack"), "striker_isolated"))
                .as("bant içi ama kutuya koşmayan roller sayılmaz").isPresent();
    }

    @Test
    void lonelyStriker_doesNotTriggerWithALateRunnerOrAWideWinger() {
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack", "LCM=half_winger_attack"), "striker_isolated")).as("kutuya geç gelen var").isEmpty();
        assertThat(finding(run(50, Map.of(), "LW=inside_forward_attack"), "striker_isolated")).as("bir kanat çizgide").isEmpty();
    }
}
