package com.fcareer.fitengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.RuleFinding;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fcareer.fitengine.FitFixtures.HUNGARIAN;
import static com.fcareer.fitengine.FitFixtures.INTENT_FIT;
import static com.fcareer.fitengine.FitFixtures.ROLE_FIT;
import static com.fcareer.fitengine.FitFixtures.SQUAD_FIT;
import static com.fcareer.fitengine.FitFixtures.classic10;
import static com.fcareer.fitengine.FitFixtures.defender;
import static com.fcareer.fitengine.FitFixtures.fullback;
import static com.fcareer.fitengine.FitFixtures.holding;
import static com.fcareer.fitengine.FitFixtures.insideForward;
import static com.fcareer.fitengine.FitFixtures.player;
import static com.fcareer.fitengine.FitFixtures.slot;
import static com.fcareer.fitengine.FitFixtures.striker;
import static com.fcareer.fitengine.FitFixtures.tag;
import static com.fcareer.fitengine.FitFixtures.winger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FitEngineScenarioTest {

    @Test
    @DisplayName("T1 — Weapon gate: PS+ hardMin'i -4 telafi eder, YELLOW vs RED ve projected tavanı")
    void t1WeaponGatePlaystylePlusCompensation() {
        FitPlayer withPsPlus = player(1, "A", "LW").attr(Attr.FINISHING, 64)
                .playstyle("FinesseShot", 2).accelerate(AcceleRate.EXPLOSIVE).build();
        FitPlayer withoutPs = player(2, "B", "LW").attr(Attr.FINISHING, 64)
                .accelerate(AcceleRate.EXPLOSIVE).build();

        RoleFitResponse a = ROLE_FIT.calculate(withPsPlus, insideForward());
        RoleFitResponse b = ROLE_FIT.calculate(withoutPs, insideForward());

        assertThat(a.getWeaponChecks().get(0).getEffectiveMin()).isEqualTo(62);
        assertThat(a.getBadge()).isEqualTo(WeaponState.YELLOW);
        assertThat(a.getBreakdown().getWeaponPenalty()).isEqualTo(5.6);
        assertThat(a.getProjectedRating()).isLessThanOrEqualTo(82);

        assertThat(b.getWeaponChecks().get(0).getEffectiveMin()).isEqualTo(66);
        assertThat(b.getBadge()).isEqualTo(WeaponState.RED);
        assertThat(b.getBreakdown().getWeaponPenalty()).isEqualTo(7.8);
        assertThat(b.getProjectedRating()).isLessThanOrEqualTo(74);

        assertThat(a.getBadgeText()).startsWith("İş görür ama Bitiricilik zayıf");
        assertThat(b.getBadgeText()).startsWith("Rolün özünü oynayamaz: Bitiricilik");
        assertThat(a.getReasons()).anyMatch(r -> r.contains("PlayStyle+ eşiği telafi ediyor"));
        assertThat(a.getScore()).isGreaterThan(b.getScore() + 5);
        assertThat(a.getProjectedRating()).isGreaterThan(b.getProjectedRating());
    }

    @Test
    @DisplayName("T2 — IntentFit düşer: Crossing 55, 'orta açsın' beklenti 68 → ok=false, RoleFit yüksek kalır")
    void t2IntentFitDropsWhileRoleFitStaysHigh() {
        FitPlayer winger = player(3, "Kanat", "RW").all(90).attr(Attr.CROSSING, 55)
                .attr(Attr.ACCELERATION, 92).accelerate(AcceleRate.EXPLOSIVE).build();
        FitTag cross = tag("w_cross", "W", Attr.CROSSING, 68);
        IntentProfile intent = slot("RW", "RW", "W", winger(), cross);

        RoleFitResponse roleFit = ROLE_FIT.calculate(winger, winger(), intent, "RW");
        IntentFitResponse intentFit = INTENT_FIT.calculate(winger, intent);

        assertThat(intentFit.getPerTag()).hasSize(1);
        assertThat(intentFit.getPerTag().get(0).getRatio()).isCloseTo(0.809, within(0.001));
        assertThat(intentFit.getPerTag().get(0).isOk()).isFalse();
        assertThat(intentFit.getScore()).isEqualTo(81);
        assertThat(roleFit.getBadge()).isEqualTo(WeaponState.GREEN);
        assertThat(roleFit.getScore()).isGreaterThanOrEqualTo(75);
    }

    @Test
    @DisplayName("T3 — Hungarian: greedy'nin yanıldığı 11x11 fikstürde toplam maksimize edilir")
    void t3HungarianBeatsGreedy() {
        int n = 11;
        double[][] scores = new double[n][n];
        for (int p = 0; p < n; p++) {
            for (int s = 0; s < n; s++) {
                scores[p][s] = p == s ? 70 : 30;
            }
        }
        scores[0][0] = 95;
        scores[0][1] = 90;
        scores[1][0] = 88;
        scores[1][1] = 40;

        int[] assignment = HUNGARIAN.maximize(scores);

        assertThat(assignment[0]).isEqualTo(1);
        assertThat(assignment[1]).isEqualTo(0);
        assertThat(total(scores, assignment)).isEqualTo(90 + 88 + 9 * 70);
        assertThat(total(scores, assignment)).isGreaterThan(greedyTotal(scores));
        assertThat(Set.of(toBoxed(assignment))).hasSize(n);
    }

    @Test
    @DisplayName("T3b — SquadFit: iki slotta en iyi oyuncu tek slota atanır, diğerine 2. en iyi gider")
    void t3SquadFitAssignsEachPlayerOnce() {
        FitPlayer star = player(10, "Yıldız", "ST", "RW").all(85).accelerate(AcceleRate.EXPLOSIVE).build();
        FitPlayer winger = player(11, "Kanatçı", "RW").all(78).accelerate(AcceleRate.EXPLOSIVE).build();
        FitPlayer weakStriker = player(12, "Zayıf 9", "ST").all(58).accelerate(AcceleRate.EXPLOSIVE).build();
        List<IntentProfile> slots = List.of(slot("ST", "ST", "ST", striker()), slot("RW", "RW", "W", insideForward()));

        SquadFitResponse response = SQUAD_FIT.solve(List.of(star, winger, weakStriker), slots, List.of());

        Map<String, Long> bySlot = response.getSlots().stream()
                .collect(Collectors.toMap(SlotAssignment::getSlotId, SlotAssignment::getPlayerId));
        assertThat(bySlot).containsEntry("ST", 10L).containsEntry("RW", 11L);
        assertThat(response.getBench()).containsExactly(12L);
        assertThat(response.getSlots().get(0).getDepth()).extracting("playerId").containsExactly(12L);
    }

    @Test
    @DisplayName("T4 — Kontra riski: iki AWB-Attack + CB pace 58/61 + tek pivot → RISK kartı + fix")
    void t4CounterAttackRiskRule() throws IOException {
        List<TacticRuleDefinition> rules = loadSeedRules();
        List<IntentProfile> slots = counterRiskSlots(1);
        List<FitPlayer> squad = counterRiskSquad();

        SquadFitResponse response = SQUAD_FIT.solve(squad, slots, rules);

        RuleFinding finding = response.getRules().stream()
                .filter(r -> r.getRuleId().equals("kontra_riski_yuksek"))
                .findFirst()
                .orElseThrow();
        assertThat(finding.getTitle()).isEqualTo("Kontra riski YÜKSEK");
        assertThat(finding.getFix()).isNotBlank();
    }

    @Test
    @DisplayName("T4b — Kontra riski: ikinci pivot eklenince kural tetiklenmez")
    void t4CounterAttackRiskNotTriggeredWithTwoPivots() throws IOException {
        List<TacticRuleDefinition> rules = loadSeedRules();
        List<FitPlayer> squad = new ArrayList<>(counterRiskSquad());
        squad.add(player(26, "Pivot 2", "CDM").all(72).attr(Attr.INTERCEPTIONS, 80).build());

        SquadFitResponse response = SQUAD_FIT.solve(squad, counterRiskSlots(2), rules);

        assertThat(response.getRules()).extracting("ruleId").doesNotContain("kontra_riski_yuksek");
    }

    @Test
    @DisplayName("T5 — AcceleRATE: aynı statlı Explosive vs Controlled → +4 vs -6 (10 puan)")
    void t5AccelerateArchetype() {
        FitPlayer explosive = player(30, "Explosive", "RW").all(80).accelerate(AcceleRate.EXPLOSIVE).build();
        FitPlayer controlled = player(31, "Controlled", "RW").all(80).accelerate(AcceleRate.CONTROLLED).build();

        RoleFitResponse e = ROLE_FIT.calculate(explosive, winger());
        RoleFitResponse c = ROLE_FIT.calculate(controlled, winger());

        assertThat(e.getBreakdown().getAccelerate()).isEqualTo(4.0);
        assertThat(c.getBreakdown().getAccelerate()).isEqualTo(-6.0);
        assertThat(e.getScore() - c.getScore()).isCloseTo(10.0, within(0.05));
    }

    @Test
    @DisplayName("T6 — Out-of-position: CB'yi CAM slotuna koy → -15 + weapon RED → dipte")
    void t6OutOfPosition() {
        FitPlayer centreBack = player(40, "Stoper", "CB").all(55)
                .attr(Attr.DEF_AWARENESS, 82).attr(Attr.STANDING_TACKLE, 82).attr(Attr.STRENGTH, 85)
                .accelerate(AcceleRate.LENGTHY).build();
        FitPlayer playmaker = player(41, "On numara", "CAM").all(84).accelerate(AcceleRate.CONTROLLED).build();

        RoleFitResponse cbAtCam = ROLE_FIT.calculate(centreBack, classic10(), null, "CAM");
        RoleFitResponse realCam = ROLE_FIT.calculate(playmaker, classic10(), null, "CAM");
        RoleFitResponse cbAtCb = ROLE_FIT.calculate(centreBack, defender(), null, "CB");

        assertThat(cbAtCam.isOutOfPosition()).isTrue();
        assertThat(cbAtCam.getBreakdown().getOutOfPosition()).isEqualTo(15.0);
        assertThat(cbAtCam.getBadge()).isEqualTo(WeaponState.RED);
        assertThat(cbAtCam.getScore()).isLessThan(realCam.getScore() - 30);
        assertThat(cbAtCb.isOutOfPosition()).isFalse();
        assertThat(cbAtCb.getScore()).isGreaterThan(cbAtCam.getScore());
    }

    @Test
    @DisplayName("Intent ağırlık bükümü normalize edilir ve davranış statı yüksek oyuncuyu öne alır")
    void intentWeaponBoostShiftsWeights() {
        FitPlayer crosser = player(50, "Ortacı", "RW").all(75).attr(Attr.CROSSING, 90).accelerate(AcceleRate.EXPLOSIVE).build();
        FitTag cross = tag("w_cross", "W", Attr.CROSSING, 68);
        IntentProfile withIntent = slot("RW", "RW", "W", winger(), cross);

        RoleFitResponse plain = ROLE_FIT.calculate(crosser, winger(), null, "RW");
        RoleFitResponse boosted = ROLE_FIT.calculate(crosser, winger(), withIntent, "RW");

        assertThat(boosted.getBreakdown().getBase()).isGreaterThan(plain.getBreakdown().getBase());
    }

    @Test
    @DisplayName("Davranış seçilmemiş slotta IntentFit nötr (100) ve combined = RoleFit")
    void noTagsMeansNeutralIntent() {
        FitPlayer p = player(60, "Forvet", "ST").all(80).accelerate(AcceleRate.EXPLOSIVE).build();
        SquadFitResponse response = SQUAD_FIT.solve(List.of(p), List.of(slot("ST", "ST", "ST", striker())), List.of());

        SlotAssignment st = response.getSlots().get(0);
        assertThat(st.getIntentFit()).isNull();
        assertThat(st.getCombined()).isEqualTo(st.getRoleFit().getScore());
        assertThat(response.getIntentFit()).isNull();
    }

    @Test
    @DisplayName("Atak paterni aynı slotun davranışlarını tek cümlede birleştirir")
    void attackPatternGroupsSentencesPerSlot() {
        FitPlayer p = player(80, "Forvet", "ST").all(80).accelerate(AcceleRate.EXPLOSIVE).build();
        IntentProfile slot = slot("ST", "ST", "ST", striker(), tag("a", "ST", Attr.FINISHING, 60), tag("b", "ST", Attr.POSITIONING, 60));

        SquadFitResponse response = SQUAD_FIT.solve(List.of(p), List.of(slot), List.of());

        assertThat(response.getAttackPattern()).startsWith("Atak paterni: ST test cümlesi a, test cümlesi b");
        assertThat(response.getAttackPattern().split("ST ", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Kadro slot sayısından azsa boş slot zayıf halka olarak raporlanır")
    void emptySlotIsReportedAsWeakLink() {
        FitPlayer p = player(70, "Tek", "ST").all(80).accelerate(AcceleRate.EXPLOSIVE).build();
        List<IntentProfile> slots = List.of(slot("ST", "ST", "ST", striker()), slot("RW", "RW", "W", insideForward()));

        SquadFitResponse response = SQUAD_FIT.solve(List.of(p), slots, List.of());

        assertThat(response.getWeakLinks()).extracting("slotId").contains("RW");
        assertThat(response.getWeakLinks().get(0).getScoutQuery().getMinAttrs()).containsKey("Finishing");
    }

    private List<IntentProfile> counterRiskSlots(int pivotCount) {
        FitTag overlap = tag("fb_overlap", "FB", Attr.STAMINA, 78);
        List<IntentProfile> slots = new ArrayList<>(List.of(
                slot("LB", "LB", "FB", fullback(), overlap),
                slot("RB", "RB", "FB", fullback(), overlap),
                slot("LCB", "CB", "CB", defender()),
                slot("RCB", "CB", "CB", defender()),
                slot("CDM", "CDM", "CDM", holding())));
        if (pivotCount > 1) {
            slots.add(slot("CDM2", "CDM", "CDM", holding()));
        }
        return slots;
    }

    private List<FitPlayer> counterRiskSquad() {
        return List.of(
                player(20, "Sol bek", "LB").all(70).attr(Attr.CROSSING, 82).attr(Attr.STAMINA, 88)
                        .accelerate(AcceleRate.EXPLOSIVE).build(),
                player(21, "Sağ bek", "RB").all(70).attr(Attr.CROSSING, 80).attr(Attr.STAMINA, 86)
                        .accelerate(AcceleRate.EXPLOSIVE).build(),
                player(22, "Yavaş stoper 1", "CB").all(60).attr(Attr.DEF_AWARENESS, 80).attr(Attr.STANDING_TACKLE, 80)
                        .attr(Attr.STRENGTH, 84).attr(Attr.ACCELERATION, 58).attr(Attr.SPRINT_SPEED, 58)
                        .accelerate(AcceleRate.LENGTHY).build(),
                player(23, "Yavaş stoper 2", "CB").all(60).attr(Attr.DEF_AWARENESS, 78).attr(Attr.STANDING_TACKLE, 79)
                        .attr(Attr.STRENGTH, 82).attr(Attr.ACCELERATION, 61).attr(Attr.SPRINT_SPEED, 61)
                        .accelerate(AcceleRate.LENGTHY).build(),
                player(24, "Çapa", "CDM").all(72).attr(Attr.INTERCEPTIONS, 82).attr(Attr.DEF_AWARENESS, 80).build());
    }

    private List<TacticRuleDefinition> loadSeedRules() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/seed/tactic-rules.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {
            });
        }
    }

    private static double total(double[][] scores, int[] assignment) {
        double sum = 0;
        for (int p = 0; p < assignment.length; p++) {
            sum += scores[p][assignment[p]];
        }
        return sum;
    }

    private static double greedyTotal(double[][] scores) {
        int n = scores.length;
        Set<Integer> usedPlayers = new HashSet<>();
        Set<Integer> usedSlots = new HashSet<>();
        double sum = 0;
        for (int k = 0; k < n; k++) {
            double best = -1;
            int bp = -1;
            int bs = -1;
            for (int p = 0; p < n; p++) {
                for (int s = 0; s < n; s++) {
                    if (!usedPlayers.contains(p) && !usedSlots.contains(s) && scores[p][s] > best) {
                        best = scores[p][s];
                        bp = p;
                        bs = s;
                    }
                }
            }
            usedPlayers.add(bp);
            usedSlots.add(bs);
            sum += best;
        }
        return sum;
    }

    private static Integer[] toBoxed(int[] values) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return boxed;
    }
}
