package com.fcareer.advisor;

import com.fcareer.advisor.service.BestPositionRule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KARIYER-UX FAZ 3.1 (BEHAVIOR CHANGE): "En iyi mevki" simetrik mevkilerde (LM/RM, LB/RB, LW/RW, LWB/RWB) sola kaymasın.
 * Kurallar: (0) listelenen bir mevki en iyiye eşik kadar yakınsa (varsayılan 1) o gösterilir; (1) simetrik eşitlikte listelenen taraf;
 * (2) iki taraf da listede ya da hiçbiri yoksa tercih edilen ayak; (3) o da yoksa ilk listelenen mevkinin tarafı.
 * Örnekler ekran görüntüsündeki oyunculardan (McGowan, Gutiérrez, Hall) türetilmiş sentetik değerler.
 */
class BestPositionRuleTest {

    private static Map<String, Integer> overalls(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void mcgowanCbRbIsNotShownAsLeftBack() {
        // LB ve RB simetrik eşit, CB bir puan altında; listelenen RB en iyi → RB.
        Map<String, Integer> o = overalls("LB", 68, "RB", 68, "CB", 67, "CDM", 64);
        assertThat(BestPositionRule.pick(o, List.of("CB", "RB"), "Right", 1).getKey()).isEqualTo("RB");
    }

    @Test
    void gutierrezAndHallRightSidedMidfieldersStayRight() {
        Map<String, Integer> o = overalls("LM", 66, "RM", 66, "LW", 65, "RW", 65, "LB", 63, "RB", 63);
        assertThat(BestPositionRule.pick(o, List.of("RM", "RB", "RW"), "Right", 1).getKey()).as("Gutiérrez").isEqualTo("RM");
        assertThat(BestPositionRule.pick(o, List.of("RM", "CM"), null, 1).getKey()).as("Hall").isEqualTo("RM");
    }

    @Test
    void symmetricTieUsesListedSideThenFootThenFirstListed() {
        Map<String, Integer> o = overalls("LW", 70, "RW", 70, "ST", 64);
        assertThat(BestPositionRule.pick(o, List.of("ST", "RW"), "Left", 0).getKey()).as("listede yalnız sağ taraf").isEqualTo("RW");
        assertThat(BestPositionRule.pick(o, List.of("ST", "LM", "RM"), "Left", 0).getKey()).as("iki taraf da listede → sol ayak").isEqualTo("LW");
        assertThat(BestPositionRule.pick(o, List.of("ST"), "Right", 0).getKey()).as("hiçbir taraf listede değil → sağ ayak").isEqualTo("RW");
        assertThat(BestPositionRule.pick(o, List.of("LM", "RM"), null, 0).getKey()).as("ayak yok → ilk listelenenin tarafı").isEqualTo("LW");
    }

    @Test
    void listedPositionWithinThresholdWinsOverUnlistedBest() {
        // CM oyuncusunda LM bir puan yüksek çıkması "en iyi mevki LM" demek değil.
        Map<String, Integer> o = overalls("LM", 72, "RM", 72, "CM", 71, "CAM", 70);
        assertThat(BestPositionRule.pick(o, List.of("CM", "CDM"), "Right", 1)).isEqualTo(Map.entry("CM", 71));
        assertThat(BestPositionRule.pick(o, List.of("CM", "CDM"), "Right", 0).getKey()).as("eşik 0 → en iyi (sağ ayak)").isEqualTo("RM");
        Map<String, Integer> far = overalls("LM", 74, "RM", 74, "CM", 71);
        assertThat(BestPositionRule.pick(far, List.of("CM"), "Right", 1).getKey()).as("eşiğin dışında").isEqualTo("RM");
    }

    @Test
    void sideOrderSortsSymmetricTiesOfTheAdviceList() {
        // Öneri listesi (en iyi 3) de aynı kuralla: eşit puanlı simetrik mevkilerde sağ taraf oyuncunun sağı önce gelir.
        List<String> sorted = BestPositionRule.sideFirst(List.of("LM", "RM", "CM"), List.of("RM", "RB"), "Right");
        assertThat(sorted).containsExactly("RM", "LM", "CM");
    }
}
