package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-7 (13 §3.4): `seed/role-movement.fc27.json` hareket profili değişmezleri. Sayılar EA rol tanımından TASLAK ve doğrulanmamıştır (verified:false);
 * asıl güvence değerlerin kendisi değil rollerin birbirine göre SIRASIDIR: kalibrasyon değişse de bu sıralar bozulamaz.
 */
class RoleMovementInvariantsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> FIELDS = List.of("dxIn", "dyFwd", "w", "r", "d", "p", "s", "cross", "cutIn", "runBehind", "longShot");
    /** İşaretli hareket: dxIn < 0 = kanada açılır, dyFwd < 0 = geriye düşer. */
    private static final double DX_MIN = -15;
    private static final double DX_MAX = 30;
    private static final double DY_MIN = -12;
    private static final double DY_MAX = 35;

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    private static Map<String, JsonNode> movement() throws Exception {
        Map<String, JsonNode> byId = new HashMap<>();
        read("seed/role-movement.fc27.json").path("roles").forEach((n) -> byId.put(n.path("id").asText(), n));
        return byId;
    }

    private static double v(Map<String, JsonNode> m, String id, String field) {
        assertThat(m).as("rol/focus satırı: " + id).containsKey(id);
        return m.get(id).path(field).asDouble();
    }

    @Test
    void everyRoleFocusCombinationHasOneRowWithAllElevenFieldsInRangeAndIsUnverifiedWithABasis() throws Exception {
        Map<String, JsonNode> m = movement();
        Set<String> expected = new HashSet<>();
        read("seed/roles.json").forEach((role) -> role.path("focuses").forEach((f) ->
                expected.add(role.path("id").asText() + "_" + f.asText().toLowerCase(java.util.Locale.ROOT).replace('-', '_'))));
        assertThat(m.keySet()).as("roles.json ile birebir (rol × focus)").isEqualTo(expected);
        m.forEach((id, row) -> {
            for (String field : FIELDS) {
                assertThat(row.has(field)).as(id + "." + field).isTrue();
                double value = row.path(field).asDouble();
                double min = field.equals("dxIn") ? DX_MIN : field.equals("dyFwd") ? DY_MIN : 0.0;
                double max = field.equals("dxIn") ? DX_MAX : field.equals("dyFwd") ? DY_MAX : 1.0;
                assertThat(value).as(id + "." + field).isBetween(min, max);
            }
            assertThat(row.path("verified").asBoolean(true)).as(id + " verified").isFalse();
            assertThat(row.path("_basis").asText()).as(id + " _basis").isNotBlank();
        });
    }

    @Test
    void theSeedItselfIsMarkedUnverifiedAndStatesItsBasis() throws Exception {
        JsonNode root = read("seed/role-movement.fc27.json");
        assertThat(root.path("verified").asBoolean(true)).isFalse();
        assertThat(root.path("_basis").asText()).contains("oyun içi gözlem");
    }

    @Test
    void buggraDraftCorrectionsArePinned() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "fullback_defend", "dxIn")).isEqualTo(1.0);
        assertThat(v(m, "holding_defend", "p")).isEqualTo(0.35);
        assertThat(v(m, "attacking_wingback_attack", "d")).isEqualTo(0.30);
    }

    @Test
    void forwardDisplacementOrder_awbAttack_wingback_fullbackBalanced_fullbackDefend() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "attacking_wingback_attack", "dyFwd")).isGreaterThan(v(m, "wingback_balanced", "dyFwd"));
        assertThat(v(m, "wingback_balanced", "dyFwd")).isGreaterThan(v(m, "fullback_balanced", "dyFwd"));
        assertThat(v(m, "fullback_balanced", "dyFwd")).isGreaterThan(v(m, "fullback_defend", "dyFwd"));
    }

    @Test
    void inwardShiftOrder_insideForward_widePlaymaker_winger() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "inside_forward_attack", "dxIn")).isGreaterThan(v(m, "wide_playmaker_attack", "dxIn"));
        assertThat(v(m, "wide_playmaker_attack", "dxIn")).isGreaterThan(v(m, "winger_balanced", "dxIn"));
    }

    @Test
    void widthTendency_winger_overInsideForward() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "winger_balanced", "w")).isGreaterThan(v(m, "inside_forward_attack", "w"));
    }

    @Test
    void defensiveDutyOrder_holding_dlp_boxToBox_playmaker_and_centreHalf_overBoxCrasher() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "holding_defend", "d")).isGreaterThan(v(m, "deep_lying_playmaker_defend", "d"));
        assertThat(v(m, "deep_lying_playmaker_defend", "d")).isGreaterThan(v(m, "box_to_box_balanced", "d"));
        assertThat(v(m, "box_to_box_balanced", "d")).isGreaterThan(v(m, "playmaker_balanced", "d"));
        assertThat(v(m, "centre_half_defend", "d")).isGreaterThan(v(m, "box_crasher_balanced", "d"));
    }

    @Test
    void forwardRunOrder_af_poacher_targetForward_falseNine() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "advanced_forward_attack", "r")).isGreaterThan(v(m, "poacher_attack", "r"));
        assertThat(v(m, "poacher_attack", "r")).isGreaterThan(v(m, "target_forward_attack", "r"));
        assertThat(v(m, "target_forward_attack", "r")).isGreaterThan(v(m, "false_9_attack", "r"));
    }

    @Test
    void staminaCostOrder_boxToBox_atLeastAwb_overFullbackDefend_overHolding() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "box_to_box_balanced", "s")).isGreaterThanOrEqualTo(v(m, "attacking_wingback_attack", "s"));
        assertThat(v(m, "attacking_wingback_attack", "s")).isGreaterThan(v(m, "fullback_defend", "s"));
        assertThat(v(m, "fullback_defend", "s")).isGreaterThan(v(m, "holding_defend", "s"));
    }

    @Test
    void pressOrder_stopper_overDefender_inEveryFocus() throws Exception {
        Map<String, JsonNode> m = movement();
        double lowestStopper = Math.min(v(m, "stopper_balanced", "p"), v(m, "stopper_aggressive", "p"));
        double highestDefender = Math.max(v(m, "defender_defend", "p"), v(m, "defender_balanced", "p"));
        assertThat(lowestStopper).isGreaterThan(highestDefender);
    }

    @Test
    void withinOneRole_attackNeverRunsLessThanBalancedNorDefend_andDefendNeverTracksBackLess() throws Exception {
        Map<String, JsonNode> m = movement();
        Map<String, Integer> rank = Map.of("defend", 0, "balanced", 1, "attack", 2);
        for (String idA : m.keySet()) {
            for (String idB : m.keySet()) {
                String roleA = m.get(idA).path("role").asText();
                String focusA = m.get(idA).path("focus").asText().toLowerCase(java.util.Locale.ROOT);
                String focusB = m.get(idB).path("focus").asText().toLowerCase(java.util.Locale.ROOT);
                if (roleA.equals(m.get(idB).path("role").asText()) && rank.containsKey(focusA) && rank.containsKey(focusB) && rank.get(focusA) > rank.get(focusB)) {
                    assertThat(v(m, idA, "r")).as(idA + " r ≥ " + idB).isGreaterThanOrEqualTo(v(m, idB, "r"));
                    assertThat(v(m, idA, "dyFwd")).as(idA + " dyFwd ≥ " + idB).isGreaterThanOrEqualTo(v(m, idB, "dyFwd"));
                    assertThat(v(m, idA, "d")).as(idA + " d ≤ " + idB).isLessThanOrEqualTo(v(m, idB, "d"));
                }
            }
        }
    }

    @Test
    void opportunityChannelOrders_crossersCross_cutInnersCutIn_runnersRunBehind_shootersShoot() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "winger_attack", "cross")).isGreaterThan(v(m, "inside_forward_attack", "cross"));
        assertThat(v(m, "inside_forward_attack", "cutIn")).isGreaterThan(v(m, "winger_balanced", "cutIn"));
        assertThat(v(m, "advanced_forward_attack", "runBehind")).isGreaterThan(v(m, "target_forward_attack", "runBehind"));
        assertThat(v(m, "shadow_striker_attack", "runBehind")).isGreaterThan(v(m, "holding_defend", "runBehind"));
        assertThat(v(m, "box_crasher_balanced", "longShot")).isGreaterThan(v(m, "centre_half_defend", "longShot"));
        assertThat(v(m, "attacking_wingback_attack", "cross")).isGreaterThan(v(m, "fullback_defend", "cross"));
    }

    @Test
    void signedMovement_falseNineDropsBackWhileAdvancedForwardPushesOn_dlpDropsBetweenCentreBacks() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "false_9_build_up", "dyFwd")).isLessThan(v(m, "false_9_attack", "dyFwd"));
        assertThat(v(m, "false_9_attack", "dyFwd")).isNegative();
        assertThat(v(m, "advanced_forward_attack", "dyFwd")).isPositive();
        assertThat(v(m, "deep_lying_playmaker_build_up", "dyFwd")).isLessThan(v(m, "holding_defend", "dyFwd"));
    }

    @Test
    void signedMovement_widePlayersOpenOutToTheTouchline() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "target_forward_wide", "dxIn")).isNegative();
        assertThat(v(m, "wide_back_support", "dxIn")).isNegative();
        assertThat(v(m, "wide_half_build_up", "dxIn")).isNegative();
    }

    @Test
    void signedMovementCorrectionsArePinnedAndMarkedWithTheirBasis() throws Exception {
        Map<String, JsonNode> m = movement();
        assertThat(v(m, "false_9_build_up", "dyFwd")).isEqualTo(-8);
        assertThat(v(m, "false_9_attack", "dyFwd")).isEqualTo(-5);
        assertThat(v(m, "deep_lying_playmaker_build_up", "dyFwd")).isEqualTo(-4);
        assertThat(v(m, "target_forward_wide", "dxIn")).isEqualTo(-12);
        assertThat(v(m, "wide_back_defend", "dxIn")).isEqualTo(-2);
        assertThat(v(m, "wide_back_support", "dxIn")).isEqualTo(-6);
        assertThat(v(m, "wide_back_aggressive", "dxIn")).isEqualTo(-5);
        assertThat(v(m, "wide_half_defend", "dxIn")).isEqualTo(-6);
        assertThat(v(m, "wide_half_build_up", "dxIn")).isEqualTo(-8);
        for (String id : List.of("false_9_build_up", "false_9_attack", "deep_lying_playmaker_build_up", "target_forward_wide", "wide_back_defend", "wide_back_support", "wide_back_aggressive", "wide_half_defend", "wide_half_build_up")) {
            assertThat(m.get(id).path("_basis").asText()).as(id).contains("işaretli hareket düzeltmesi, Buğra/Claude incelemesi");
            assertThat(m.get(id).path("verified").asBoolean(true)).as(id).isFalse();
        }
    }
}
