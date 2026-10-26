package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.service.PositionOverallFixture;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
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
    private final GrowthProjector growth = new GrowthProjector(new GrowthProjector.Config(), PositionOverallFixture.loaded(MAPPER));
    private final DevelopmentPlans plans = new DevelopmentPlans(MAPPER);
    private static final PositionOverallService OVERALL = PositionOverallFixture.loaded(MAPPER);
    private final PlanProjector projector = new PlanProjector(growth, OVERALL);

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

    /** Karar: plan yalnız dağılımı değiştirir; ana mevkideki tahmini OVR artışı Dengeli ile aynı, hiçbir planda OVR POT'u geçmez. */
    @Test
    void noPlanPassesPotentialIncludingBalanced() {
        FitPlayer striker = player("ST").toBuilder().overall(64).potential(66).build();
        for (Plan plan : List.of(plans.general(), plans.positionPlan("ST").orElseThrow(), plans.positionPlan("CB").orElseThrow(), plans.rolePlan("poacher", "ST").orElseThrow())) {
            assertThat(projector.project(striker, plan, 2).overall()).as(plan.key()).isLessThanOrEqualTo(66);
        }
    }

    @Test
    void everyPlanKeepsTheBalancedOverallAtTheMainPosition() {
        FitPlayer striker = player("ST");
        for (int seasons = 1; seasons <= 2; seasons++) {
            int balanced = projector.project(striker, plans.general(), seasons).overall();
            for (Plan plan : List.of(plans.positionPlan("ST").orElseThrow(), plans.positionPlan("CB").orElseThrow(), plans.rolePlan("poacher", "ST").orElseThrow())) {
                assertThat(projector.project(striker, plan, seasons).overall()).as(plan.key() + " " + seasons + " sezon").isEqualTo(balanced);
            }
        }
    }

    @Test
    void planAttributesAreScaledSoTheMainPositionFormulaGainMatchesBalanced() {
        // Ana mevki formülü doğrusal: Σ ağırlık × attribute artışı. Plan bu toplamı Dengeli'ye eşitler (yuvarlama payı ±1); dağılım farklı kalır.
        FitPlayer striker = player("ST");
        Map<Attr, Double> w = OVERALL.weights("ST");
        double balanced = gain(w, striker, projector.project(striker, plans.general(), 1).grown());
        FitPlayer planned = projector.project(striker, plans.positionPlan("ST").orElseThrow(), 1).grown();
        assertThat(gain(w, striker, planned)).isCloseTo(balanced, org.assertj.core.api.Assertions.within(1.0));
        assertThat(planned.getAttrs().get(Attr.FINISHING) - 62).as("plan attribute'u yine öne çıkar")
                .isGreaterThan(planned.getAttrs().get(Attr.STAMINA) - 62);
    }

    /**
     * GT-a düzeltmesi: düşüş yaşında plan etkili — plan attribute'ları Dengeli'den yavaş düşer, kayıp plan dışına kayar; kart OVR attribute'lardan
     * (ST'yi koruyan Poacher planıyla Dengeli'den az düşer, ama hiç artmaz).
     */
    @Test
    void inDeclineAPlanProtectsItsAttributesAndTheOverallFollowsThem() {
        FitPlayer veteran = player("ST").toBuilder().age(36).overall(80).potential(80).build();
        Plan poacher = plans.rolePlan("poacher", "ST").orElseThrow();
        PlanProjection balanced = projector.project(veteran, plans.general(), 1);
        PlanProjection planned = projector.project(veteran, poacher, 1);
        assertThat(balanced.overall()).isLessThan(80);
        assertThat(planned.overall()).as("Poacher ST'nin ağır attribute'larını korur").isGreaterThan(balanced.overall());
        assertThat(planned.overall()).as("düşüş artışa dönmez").isLessThanOrEqualTo(80);
        assertThat(projector.project(veteran, poacher, 2).overall()).as("iki sezonda da plan önde").isGreaterThan(projector.project(veteran, plans.general(), 2).overall());
        assertThat(planned.grown().getAttrs().get(Attr.FINISHING)).as("Poacher planı bitiriciliği korur").isGreaterThan(balanced.grown().getAttrs().get(Attr.FINISHING));
        assertThat(planned.grown().getRoleFamiliarity()).containsEntry("poacher", 1);
    }

    private static double gain(Map<Attr, Double> w, FitPlayer before, FitPlayer after) {
        return w.entrySet().stream().mapToDouble((e) -> e.getValue() * (after.getAttrs().get(e.getKey()) - before.getAttrs().get(e.getKey()))).sum();
    }
}
