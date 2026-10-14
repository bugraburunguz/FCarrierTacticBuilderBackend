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
 * E3-7 analiz kuralları: her kural için en az 1 tetikleyen + 1 tetiklemeyen fixture. Etiket YOK: bulgular yalnız roller (seed), konumlar ve (varsa) oyuncu attribute'larından çıkar.
 * Eşikler verified:false; testler eşiklerin kendisini değil kuralın yönünü ve kanıtının sayısal olduğunu sabitler.
 */
class TacticRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RoleMovements movements = new RoleMovements(MAPPER);
    private final TacticRules rules = new TacticRules(MAPPER);
    private final MetricsCalculator calculator = new MetricsCalculator();
    private final TacticAnalyzer analyzer = new TacticAnalyzer(movements, rules, calculator, new RuleEngine(rules, new RuleMetrics(calculator)));

    /** 4-2-3-1 (LM-CAM-RM), formations.json konumları ve varsayılan roller. */
    private static List<String[]> base4231() {
        return List.of(new String[]{"GK", "GK", "50", "95", "goalkeeper_defend"}, new String[]{"LB", "LB", "12", "75", "fullback_balanced"}, new String[]{"LCB", "CB", "37", "80", "defender_defend"},
                new String[]{"RCB", "CB", "63", "80", "defender_defend"}, new String[]{"RB", "RB", "88", "75", "fullback_balanced"}, new String[]{"LDM", "CDM", "38", "60", "holding_defend"},
                new String[]{"RDM", "CDM", "62", "60", "holding_defend"}, new String[]{"LM", "LM", "10", "45", "winger_balanced"}, new String[]{"CAM", "CAM", "50", "34", "playmaker_balanced"},
                new String[]{"RM", "RM", "90", "45", "winger_balanced"}, new String[]{"ST", "ST", "50", "14", "advanced_forward_attack"});
    }

    private static List<String[]> base433() {
        return List.of(new String[]{"GK", "GK", "50", "95", "goalkeeper_defend"}, new String[]{"LB", "LB", "12", "75", "fullback_balanced"}, new String[]{"LCB", "CB", "37", "80", "defender_defend"},
                new String[]{"RCB", "CB", "63", "80", "defender_defend"}, new String[]{"RB", "RB", "88", "75", "fullback_balanced"}, new String[]{"CDM", "CDM", "50", "62", "holding_defend"},
                new String[]{"LCM", "CM", "35", "45", "box_to_box_balanced"}, new String[]{"RCM", "CM", "65", "45", "box_to_box_balanced"}, new String[]{"LW", "LW", "15", "20", "winger_balanced"},
                new String[]{"RW", "RW", "85", "20", "winger_balanced"}, new String[]{"ST", "ST", "50", "14", "advanced_forward_attack"});
    }

    private static List<String[]> base442() {
        return List.of(new String[]{"GK", "GK", "50", "95", "goalkeeper_defend"}, new String[]{"LB", "LB", "12", "75", "fullback_balanced"}, new String[]{"LCB", "CB", "37", "80", "defender_defend"},
                new String[]{"RCB", "CB", "63", "80", "defender_defend"}, new String[]{"RB", "RB", "88", "75", "fullback_balanced"}, new String[]{"LM", "LM", "10", "45", "winger_balanced"},
                new String[]{"LCM", "CM", "38", "50", "box_to_box_balanced"}, new String[]{"RCM", "CM", "62", "50", "box_to_box_balanced"}, new String[]{"RM", "RM", "90", "45", "winger_balanced"},
                new String[]{"LST", "ST", "40", "14", "advanced_forward_attack"}, new String[]{"RST", "ST", "60", "14", "poacher_attack"});
    }

    private Analysis run(List<String[]> base, BuildUpType buildUp, int depth, Map<String, FitPlayer> players, String... overrides) {
        Map<String, String> roleBySlot = new HashMap<>();
        for (String o : overrides) {
            roleBySlot.put(o.split("=")[0], o.split("=")[1]);
        }
        List<Slot> slots = new ArrayList<>();
        for (String[] s : base) {
            slots.add(new Slot(s[0], s[1], Integer.parseInt(s[2]), Integer.parseInt(s[3]), roleBySlot.getOrDefault(s[0], s[4])));
        }
        return analyzer.analyze(new Input("test", buildUp, depth, slots, players));
    }

    private Analysis run(BuildUpType buildUp, int depth, String... overrides) {
        return run(base4231(), buildUp, depth, Map.of(), overrides);
    }

    private static Optional<Finding> finding(Analysis a, String code) {
        return a.findings().stream().filter((f) -> f.code().equals(code)).findFirst();
    }

    private static Optional<Finding> finding(Analysis a, String code, String side) {
        return a.findings().stream().filter((f) -> f.code().equals(code) && side.equals(f.instance().get("side"))).findFirst();
    }

    private static FitPlayer player(Map<Attr, Integer> attrs, Integer height, String foot) {
        return FitPlayer.builder().id(1L).name("P").attrs(new EnumMap<>(attrs.isEmpty() ? new EnumMap<>(Attr.class) : attrs)).positions(List.of()).playstyles(Map.of()).heightCm(height).preferredFoot(foot).build();
    }

    private static Map<Attr, Integer> attrs(int value, Attr... overridden) {
        Map<Attr, Integer> m = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            m.put(a, value);
        }
        return m;
    }

    private static Map<String, FitPlayer> everyone(List<String[]> base, Map<Attr, Integer> attrs, Integer height) {
        Map<String, FitPlayer> players = new HashMap<>();
        for (String[] s : base) {
            players.put(s[0], player(attrs, height, "Right"));
        }
        return players;
    }

    // ---- koridorda genişlik yok -------------------------------------------------------------------------------------------------

    @Test
    void corridorWithoutWidth_triggersWhenTheWingerCutsInAndTheFullbackStaysHome() {
        Analysis a = run(BuildUpType.BALANCED, 55, "LM=inside_forward_attack", "LB=fullback_defend");
        Finding f = finding(a, "corridor_no_width", "LEFT").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.WARN);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("SIDE_WINGER_CUTIN_MAX", "SIDE_FULLBACK_DYFWD_MAX");
        assertThat(f.evidence().get(0).value()).isGreaterThanOrEqualTo(f.evidence().get(0).threshold());
        assertThat(f.recommendation().code()).isNotBlank();
        assertThat(f.text()).contains("0.9").contains("sol");
        assertThat(finding(a, "corridor_no_width", "RIGHT")).isEmpty();
    }

    @Test
    void corridorWithoutWidth_doesNotTriggerWhenTheFullbackOverlaps() {
        assertThat(finding(run(BuildUpType.BALANCED, 55, "LM=inside_forward_attack", "LB=attacking_wingback_attack"), "corridor_no_width", "LEFT")).isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 55), "corridor_no_width", "LEFT")).as("etiketsiz taktik, winger Balanced: kesmiyor").isEmpty();
    }

    // ---- geri savunma -----------------------------------------------------------------------------------------------------------

    @Test
    void restDefence_lowCountWarnsOnABalancedLineAndIsARiskOnAHighLine() {
        String[] weak = {"LB=attacking_wingback_attack", "RB=attacking_wingback_attack", "LDM=box_crasher_balanced", "RDM=box_crasher_balanced"};
        Analysis balanced = run(BuildUpType.BALANCED, 55, weak);
        Finding low = finding(balanced, "rest_defence_low").orElseThrow();
        assertThat(low.severity()).isEqualTo(Severity.WARN);
        assertThat(low.evidence().get(0).value()).isLessThan(3);
        assertThat(finding(balanced, "rest_defence_low_high_line")).isEmpty();
        Analysis high = run(BuildUpType.BALANCED, 80, weak);
        assertThat(finding(high, "rest_defence_low_high_line").orElseThrow().severity()).isEqualTo(Severity.RISK);
        assertThat(finding(high, "rest_defence_low")).isEmpty();
    }

    @Test
    void restDefence_isFineWhenEnoughPlayersStayBehind() {
        Analysis a = run(BuildUpType.BALANCED, 80);
        assertThat(finding(a, "rest_defence_low")).isEmpty();
        assertThat(finding(a, "rest_defence_low_high_line")).isEmpty();
        assertThat(a.metrics().restDefence().count()).isGreaterThanOrEqualTo(3);
    }

    // ---- kontra riski (oyuncu gerekir) ------------------------------------------------------------------------------------------

    @Test
    void counterRisk_triggersOnAHighLineWithSlowCentreBacks() {
        Map<String, FitPlayer> players = everyone(base4231(), attrs(60), 180);
        Finding f = finding(run(base4231(), BuildUpType.BALANCED, 80, players), "counter_risk").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.RISK);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("BAND_ORDINAL", "CB_PACE_AVG");
    }

    @Test
    void counterRisk_doesNotTriggerWithFastCentreBacksOrADeeperLine_andIsReportedAsNotEvaluatedWithoutPlayers() {
        assertThat(finding(run(base4231(), BuildUpType.BALANCED, 80, everyone(base4231(), attrs(80), 180)), "counter_risk")).isEmpty();
        assertThat(finding(run(base4231(), BuildUpType.BALANCED, 55, everyone(base4231(), attrs(60), 180)), "counter_risk")).isEmpty();
        Analysis noPlayers = run(BuildUpType.BALANCED, 80);
        assertThat(noPlayers.notEvaluated()).extracting((n) -> n.code()).contains("counter_risk");
    }

    // ---- ceza sahasında hedef yok -----------------------------------------------------------------------------------------------

    @Test
    void noBoxTarget_triggersWhenCrossVolumeIsHighAndAerialTargetsAreWeak() {
        Map<Attr, Integer> weak = attrs(50);
        Finding f = finding(run(base4231(), BuildUpType.BALANCED, 55, everyone(base4231(), weak, 170)), "no_box_target").orElseThrow();
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("CROSS_VOLUME", "AERIAL_TARGET_QUALITY");
    }

    @Test
    void noBoxTarget_doesNotTriggerWithTallAerialStrikers() {
        assertThat(finding(run(base4231(), BuildUpType.BALANCED, 55, everyone(base4231(), attrs(85), 192)), "no_box_target")).isEmpty();
    }

    // ---- dayanıklılık -----------------------------------------------------------------------------------------------------------

    @Test
    void staminaGap_triggersWhenTheDemandOutrunsThePlayers() {
        Finding f = finding(run(base4231(), BuildUpType.BALANCED, 80, everyone(base4231(), attrs(45), 180)), "stamina_gap").orElseThrow();
        assertThat(f.evidence().get(0).value()).isGreaterThan(f.evidence().get(0).threshold());
    }

    @Test
    void staminaGap_doesNotTriggerWithFitPlayers() {
        assertThat(finding(run(base4231(), BuildUpType.BALANCED, 80, everyone(base4231(), attrs(90), 180)), "stamina_gap")).isEmpty();
    }

    // ---- build-up uyumsuzluğu ---------------------------------------------------------------------------------------------------

    @Test
    void buildUpMismatch_triggersForShortBuildUpWithUnsafePassers() {
        Finding f = finding(run(base4231(), BuildUpType.SHORT, 55, everyone(base4231(), attrs(60), 180)), "buildup_mismatch").orElseThrow();
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("BUILDUP_IS", "BUILDUP_SAFETY");
    }

    @Test
    void buildUpMismatch_doesNotTriggerWithSafePassersOrAnotherBuildUp() {
        assertThat(finding(run(base4231(), BuildUpType.SHORT, 55, everyone(base4231(), attrs(80), 180)), "buildup_mismatch")).isEmpty();
        assertThat(finding(run(base4231(), BuildUpType.BALANCED, 55, everyone(base4231(), attrs(60), 180)), "buildup_mismatch")).isEmpty();
    }

    // ---- orta sahada sayısal azlık (merkezi orta saha sayımı, topsuz) -------------------------------------------------------------

    /** 4-2-4: tek merkezi orta saha (CM), kanatlar dışarıda: sayı 1. */
    private static List<String[]> thinMidfield() {
        return List.of(new String[]{"GK", "GK", "50", "95", "goalkeeper_defend"}, new String[]{"LB", "LB", "12", "75", "fullback_balanced"}, new String[]{"LCB", "CB", "37", "80", "defender_defend"},
                new String[]{"RCB", "CB", "63", "80", "defender_defend"}, new String[]{"RB", "RB", "88", "75", "fullback_balanced"}, new String[]{"CM", "CM", "50", "50", "box_to_box_balanced"},
                new String[]{"LW", "LW", "15", "25", "winger_balanced"}, new String[]{"RW", "RW", "85", "25", "winger_balanced"}, new String[]{"LST", "ST", "40", "14", "advanced_forward_attack"},
                new String[]{"RST", "ST", "60", "14", "poacher_attack"}, new String[]{"CAM", "CAM", "50", "34", "playmaker_balanced"});
    }

    @Test
    void midfieldOutnumbered_triggersWithFewerThanTwoCentralMidfielders_withoutRelyingOnTheBall() {
        List<String[]> oneCentral = new ArrayList<>(thinMidfield());
        oneCentral.removeIf((s) -> s[0].equals("CAM"));
        oneCentral.add(new String[]{"CAM", "LM", "10", "34", "wide_midfielder_support"});
        Finding f = finding(run(oneCentral, BuildUpType.BALANCED, 55, Map.of()), "midfield_outnumbered").orElseThrow();
        assertThat(f.evidence().get(0).value()).isLessThan(2);
    }

    @Test
    void midfieldOutnumbered_doesNotTriggerForTheMetaShapes_andACutInWingerCountsAsCentral() {
        assertThat(finding(run(BuildUpType.BALANCED, 55), "midfield_outnumbered")).as("4-2-3-1: 2 CDM + CAM").isEmpty();
        assertThat(finding(run(base433(), BuildUpType.BALANCED, 55, Map.of()), "midfield_outnumbered")).isEmpty();
        List<String[]> one = new ArrayList<>(thinMidfield());
        one.removeIf((s) -> s[0].equals("CAM"));
        Analysis cutIns = run(one, BuildUpType.BALANCED, 55, Map.of(), "LW=inside_forward_attack", "RW=inside_forward_attack");
        assertThat(finding(cutIns, "midfield_outnumbered")).as("içe kesen iki kanat merkezi sayılır").isEmpty();
    }

    @Test
    void midfieldThin_isOnlyInformationForAFlatFourFourTwo() {
        Analysis a = run(base442(), BuildUpType.BALANCED, 50, Map.of());
        assertThat(finding(a, "midfield_outnumbered")).isEmpty();
        assertThat(finding(a, "midfield_thin").orElseThrow().severity()).isEqualTo(Severity.INFO);
    }

    // ---- arkaya koşan yok -------------------------------------------------------------------------------------------------------

    @Test
    void noRunnerForCounter_triggersWhenCounterIsChosenAndNobodyRunsBehind() {
        Analysis a = run(BuildUpType.COUNTER, 55, "ST=false_9_build_up");
        assertThat(finding(a, "no_runner_for_counter").orElseThrow().evidence().get(1).value()).isZero();
    }

    @Test
    void noRunnerForCounter_doesNotTriggerWithARunnerOrAnotherBuildUp() {
        assertThat(finding(run(BuildUpType.COUNTER, 55), "no_runner_for_counter")).isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 55, "ST=false_9_build_up"), "no_runner_for_counter")).isEmpty();
    }

    // ---- çift false 9 / koşucusuz forvet hattı ----------------------------------------------------------------------------------

    @Test
    void doubleFalseNine_triggersForTwoFalseNines() {
        Analysis a = run(base442(), BuildUpType.BALANCED, 55, Map.of(), "LST=false_9_attack", "RST=false_9_attack");
        assertThat(finding(a, "double_false_nine").orElseThrow().evidence().get(0).value()).isEqualTo(2);
    }

    @Test
    void doubleFalseNine_doesNotTriggerForOne() {
        assertThat(finding(run(base442(), BuildUpType.BALANCED, 55, Map.of(), "LST=false_9_attack"), "double_false_nine")).isEmpty();
    }

    @Test
    void strikerLineWithoutRunner_triggersWhenNoStrikerRunsBehind_andNotWhenOneDoes() {
        Finding f = finding(run(BuildUpType.BALANCED, 55, "ST=false_9_build_up"), "striker_line_no_runner").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.INFO);
        assertThat(finding(run(BuildUpType.BALANCED, 55), "striker_line_no_runner")).isEmpty();
    }

    // ---- aynı tarafta iki bindiren: kanat açığı ---------------------------------------------------------------------------------

    @Test
    void wingGap_triggersWhenAnOverlappingFullbackAndAWideWingerBothSkipTheDefensiveWork() {
        Analysis a = run(BuildUpType.BALANCED, 55, "RB=attacking_wingback_attack");
        Finding f = finding(a, "wing_gap_overlap", "RIGHT").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.RISK);
        assertThat(f.evidence()).extracting((e) -> e.metric()).containsExactly("SIDE_FULLBACK_DYFWD_MAX", "SIDE_WINGER_W_MAX", "SIDE_DEF_DUTY_MAX");
        assertThat(finding(a, "wing_gap_overlap", "LEFT")).isEmpty();
    }

    @Test
    void wingGap_doesNotTriggerWhenOneOfThemCoversOrTheWingerStaysNarrow() {
        assertThat(finding(run(BuildUpType.BALANCED, 55, "RB=attacking_wingback_attack", "RM=wide_midfielder_defend"), "wing_gap_overlap", "RIGHT")).isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 55, "RB=fullback_defend"), "wing_gap_overlap", "RIGHT")).isEmpty();
    }

    // ---- çapa yok (yalnız savunma korumasızsa uyarı) ----------------------------------------------------------------------------

    @Test
    void noAnchor_warnsOnlyWhenTheDefenceIsAlsoExposed() {
        String[] noAnchor = {"LDM=box_crasher_balanced", "RDM=box_crasher_balanced"};
        Analysis alone = run(BuildUpType.BALANCED, 55, noAnchor);
        assertThat(finding(alone, "no_anchor")).as("tek başına uyarı değil").isEmpty();
        assertThat(finding(alone, "no_anchor_info").orElseThrow().severity()).isEqualTo(Severity.INFO);

        Analysis exposed = run(BuildUpType.BALANCED, 55, "LDM=box_crasher_balanced", "RDM=box_crasher_balanced", "LB=attacking_wingback_attack", "RB=attacking_wingback_attack");
        Finding f = finding(exposed, "no_anchor").orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.WARN);
        assertThat(f.evidence().get(0).metric()).isEqualTo("ANCHOR_COUNT");
        assertThat(f.evidence().size()).as("çapa yok + en az bir koruma açığı kanıtı").isGreaterThanOrEqualTo(2);
        assertThat(finding(exposed, "no_anchor_info")).as("uyarı varken bilgi bastırılır").isEmpty();

        Analysis highLine = run(BuildUpType.BALANCED, 80, noAnchor);
        assertThat(finding(highLine, "no_anchor")).as("yüksek hat").isPresent();
    }

    @Test
    void noAnchor_isSilentWithAHolder_andAWingbackBackFiveIsNotPunishedForPushingUp() {
        assertThat(finding(run(BuildUpType.BALANCED, 55), "no_anchor")).isEmpty();
        assertThat(finding(run(BuildUpType.BALANCED, 55), "no_anchor_info")).isEmpty();
    }

    // ---- dar kurulum ------------------------------------------------------------------------------------------------------------

    @Test
    void narrowShape_triggersWhenEveryoneTucksInside_andNotForTheDefaultShape() {
        Analysis narrow = run(BuildUpType.BALANCED, 55, "LM=inside_forward_attack", "RM=inside_forward_attack", "LB=inverted_wingback_build_up", "RB=inverted_wingback_build_up");
        assertThat(finding(narrow, "narrow_shape").orElseThrow().severity()).isEqualTo(Severity.INFO);
        assertThat(finding(run(BuildUpType.BALANCED, 55), "narrow_shape")).isEmpty();
    }

    // ---- genel ------------------------------------------------------------------------------------------------------------------

    @Test
    void everyFindingCarriesNumericEvidenceAndAGeneratedText() {
        Analysis a = run(BuildUpType.COUNTER, 80, "LM=inside_forward_attack", "LB=fullback_defend", "ST=false_9_build_up", "RB=attacking_wingback_attack");
        assertThat(a.findings()).isNotEmpty();
        for (Finding f : a.findings()) {
            assertThat(f.evidence()).as(f.code()).isNotEmpty();
            f.evidence().forEach((e) -> assertThat(e.op()).isIn("LT", "LTE", "GT", "GTE", "EQ"));
            assertThat(f.text()).as(f.code()).doesNotContain("{");
            assertThat(f.recommendation().code()).as(f.code()).isNotBlank();
        }
    }

    /** E3-8 hazırlığı: kullanıcı metninde iç kodlar (metrik adları, bant sırası, alan kısaltmaları) görünmez; kanıt tablosu ayrı alandadır. */
    @Test
    void theUserTextNeverShowsInternalCodes() {
        Analysis a = run(BuildUpType.COUNTER, 95, "LM=inside_forward_attack", "LB=fullback_defend", "ST=false_9_build_up", "RB=attacking_wingback_attack", "LDM=box_crasher_balanced", "RDM=box_crasher_balanced");
        assertThat(a.findings()).isNotEmpty();
        for (Finding f : a.findings()) {
            assertThat(f.text()).as(f.code()).doesNotContainPattern("[A-Z]{2,}_[A-Z_]+").doesNotContain("dyFwd", "cutIn", "runBehind", "bant sırası", "BAND_", "{", "}");
        }
        Map<String, FitPlayer> players = everyone(base4231(), attrs(55), 175);
        Analysis withPlayers = run(base4231(), BuildUpType.SHORT, 80, players);
        for (Finding f : withPlayers.findings()) {
            assertThat(f.text()).as(f.code()).doesNotContainPattern("[A-Z]{2,}_[A-Z_]+").doesNotContain("{", "}");
        }
    }

    @Test
    void theRuleSetIsUnverifiedAndHasAtLeastTenRulesAndNoTagBasedCondition() {
        assertThat(rules.verified()).isFalse();
        assertThat(rules.rules().size()).isGreaterThanOrEqualTo(10);
        rules.rules().forEach((r) -> r.when().forEach((c) -> assertThat(c.metric()).doesNotContain("TAG")));
    }

    @Test
    void metricsAreComputedFromTheSeedForTheWorkedExample() {
        Analysis a = run(BuildUpType.BALANCED, 55, "LB=fullback_defend", "RB=attacking_wingback_attack", "LM=inside_forward_attack", "RM=winger_balanced");
        assertThat(a.metrics().band()).isEqualTo("BALANCED");
        assertThat(a.metrics().attackDefence().attackPct()).isBetween(30.0, 60.0);
        assertThat(a.metrics().restDefence().slots()).contains("LB", "LCB", "RCB").doesNotContain("RB");
        assertThat(a.metrics().opportunity().cutIn().volume()).isGreaterThan(0.8);
        assertThat(a.metrics().stamina().supply()).as("oyuncu yok").isNull();
    }
}
