package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.DevelopmentPlans.Kind;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.fitengine.model.Attr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIT-3 madde 8: gelişim planları seed'i (Buğra'nın FC 27 tablosu). Anahtar (baseRole, mevki): aynı rol farklı mevkide farklı attribute çalıştırır.
 * Bilinmeyen attribute ya da roles.json'da olmayan (rol, mevki) çifti yüklemeyi durdurur.
 */
class DevelopmentPlansTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DevelopmentPlans plans = new DevelopmentPlans(MAPPER);

    @Test
    void seedLoadsAllPlansKeyedByRoleAndPosition() {
        assertThat(plans.general().id()).isEqualTo("plan_dengeli");
        assertThat(plans.all()).filteredOn((p) -> p.kind() == Kind.POSITION).hasSize(11);
        assertThat(plans.all()).filteredOn((p) -> p.kind() == Kind.ROLE).hasSize(37);
        Plan holdingCdm = plans.rolePlan("holding", "CDM").orElseThrow();
        Plan holdingCm = plans.rolePlan("holding", "CM").orElseThrow();
        assertThat(holdingCdm.attributes()).as("aynı rol, farklı mevki, farklı attribute").isNotEqualTo(holdingCm.attributes());
        assertThat(holdingCdm.key()).isNotEqualTo(holdingCm.key());
        assertThat(plans.rolePlan("winger", "RW").orElseThrow().attributes()).contains(Attr.INTERCEPTIONS);
        assertThat(plans.rolePlan("winger", "LM").orElseThrow().attributes()).contains(Attr.BALANCE).doesNotContain(Attr.INTERCEPTIONS);
        assertThat(plans.positionPlan("LB").orElseThrow().attributes()).isEqualTo(plans.positionPlan("RB").orElseThrow().attributes());
        assertThat(plans.positionPlan("GK")).as("kaleci mevki planı yok").isEmpty();
        assertThat(plans.rolePlans("CAM")).extracting(Plan::baseRole).contains("classic_10", "playmaker", "half_winger", "shadow_striker");
        assertThat(plans.byKey(holdingCm.key())).contains(holdingCm);
    }

    @Test
    void rolePlansDevelopRoleFamiliarityPositionPlansDoNot() {
        assertThat(plans.all()).filteredOn((p) -> p.kind() == Kind.ROLE).allMatch(Plan::roleFamiliarity);
        assertThat(plans.all()).filteredOn((p) -> p.kind() == Kind.POSITION).noneMatch(Plan::roleFamiliarity);
    }

    @Test
    void false9CarriesBugrasPositioningFixAndIssuesAreEmpty() {
        assertThat(plans.rolePlan("false_9", "ST").orElseThrow().attributes()).element(1).isEqualTo(Attr.POSITIONING);
        assertThat(plans.issues()).isEmpty();
    }

    @Test
    void extraVsPositionIsTheRolePlanMinusThePositionPlan() {
        Plan dlp = plans.rolePlan("deep_lying_playmaker", "CDM").orElseThrow();
        assertThat(plans.extraVsPosition(dlp)).containsExactlyInAnyOrder(Attr.LONG_SHOTS, Attr.CROSSING, Attr.REACTIONS, Attr.COMPOSURE);
    }

    @Test
    void seedHashMatchesItsContent() {
        assertThat(plans.hash()).isEqualTo(plans.expectedHash());
    }

    private static final String ROLES = "[{\"id\":\"winger\",\"positions\":[\"LM\",\"RM\",\"LW\",\"RW\"]},{\"id\":\"defender\",\"positions\":[\"CB\"]}]";

    @Test
    void unknownAttributeStopsLoading() {
        String json = "{\"general\":[{\"id\":\"plan_dengeli\",\"name\":\"Dengeli\"}],\"positionPlans\":[{\"id\":\"pos_cb\",\"name\":\"Stoper\",\"positions\":[\"CB\"],"
                + "\"attributes\":[\"Heading\",\"Ofan\"],\"roleFamiliarity\":false}],\"rolePlans\":[]}";
        assertThatThrownBy(() -> DevelopmentPlans.parse(MAPPER, json, ROLES)).hasMessageContaining("Ofan");
    }

    @Test
    void roleAtAPositionMissingFromRolesJsonStopsLoading() {
        String json = "{\"general\":[{\"id\":\"plan_dengeli\",\"name\":\"Dengeli\"}],\"positionPlans\":[],\"rolePlans\":[{\"id\":\"winger_cb\",\"name\":\"Winger\","
                + "\"baseRole\":\"winger\",\"positions\":[\"CB\"],\"attributes\":[\"Crossing\"],\"roleFamiliarity\":true}]}";
        assertThatThrownBy(() -> DevelopmentPlans.parse(MAPPER, json, ROLES)).hasMessageContaining("winger").hasMessageContaining("CB");
    }
}
