package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.service.PositionOverallFixture;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.development.plan.PlanProjector.PlanProjection;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-3 madde 9: plan projeksiyonu (1-2 sezon). Dengeli bugünkü modelin aynısı. Rol planı Role+ ailesini kademeli artırır (kademe/sezon config,
 * verified:false); mevki planı artırmaz. Planlı OVR etkisi position-overall-weights formülüyle (ana mevkide formül farkı).
 */
class PlanProjectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GrowthProjector growth = new GrowthProjector(new GrowthProjector.Config());
    private final DevelopmentPlans plans = new DevelopmentPlans(MAPPER);
    private final PlanProjector projector = new PlanProjector(growth, PositionOverallFixture.loaded(MAPPER));

    private static FitPlayer player(String position) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, 62);
        }
        return FitPlayer.builder().id(1L).name("Genç").age(19).overall(64).potential(80).positions(List.of(position)).attrs(attrs).playstyles(Map.of())
                .roleFamiliarity(Map.of()).gender(0).build();
    }

    @Test
    void balancedIsTodaysModelRepeated() {
        FitPlayer p = player("CM");
        PlanProjection two = projector.project(p, plans.general(), 2);
        GrowthProjector.Projection once = growth.project(p);
        assertThat(two.grown().getAttrs()).isEqualTo(growth.project(once.grown()).grown().getAttrs());
        assertThat(two.overall()).isEqualTo(growth.project(once.grown()).projectedRating());
    }

    @Test
    void rolePlanRaisesRoleFamiliarityStepByStepPositionPlanDoesNot() {
        FitPlayer p = player("CDM");
        assertThat(projector.project(p, plans.rolePlan("holding", "CDM").orElseThrow(), 1).grown().getRoleFamiliarity()).containsEntry("holding", 1);
        assertThat(projector.project(p, plans.rolePlan("holding", "CDM").orElseThrow(), 2).grown().getRoleFamiliarity()).containsEntry("holding", 2);
        assertThat(projector.project(p, plans.positionPlan("CDM").orElseThrow(), 2).grown().getRoleFamiliarity()).doesNotContainKey("holding");
    }

    @Test
    void plannedOverallFollowsThePositionFormula() {
        FitPlayer striker = player("ST");
        int stPlan = projector.project(striker, plans.positionPlan("ST").orElseThrow(), 1).overall();
        int cbPlan = projector.project(striker, plans.positionPlan("CB").orElseThrow(), 1).overall();
        assertThat(stPlan).as("forvet planı forvet OVR'sini stoper planından çok artırır").isGreaterThan(cbPlan);
        assertThat(stPlan).isGreaterThan(striker.getOverall());
    }
}
