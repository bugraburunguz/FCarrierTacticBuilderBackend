package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.service.PositionOverallFixture;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.development.service.GrowthProjector.Stage;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROWTH-CAL madde 1 (verified:false, kaynak: Buğra oyun tecrübesi, ölçülmedi): altyapıda (A takımda değil) sezon başı OVR kazancı en çok +5,
 * A takımdaki gençlerde yaş çarpanı aynen ama en çok +8. İki sezonluk tahminde altyapı oyuncusu ikinci sezonda A takımda sayılır. Tavan plan
 * dağılımından önce: plan toplamı değiştirmez, POT tavanı aynen.
 */
class GrowthCapTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GrowthProjector growth = new GrowthProjector(new GrowthProjector.Config());
    private final DevelopmentPlans plans = new DevelopmentPlans(MAPPER);
    private static final PositionOverallService OVERALL = PositionOverallFixture.loaded(MAPPER);
    private final PlanProjector projector = new PlanProjector(growth, OVERALL);

    private static FitPlayer player(int age, int ovr, int pot) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, 55);
        }
        return FitPlayer.builder().id(1L).name("Genç").age(age).overall(ovr).potential(pot).positions(List.of("CM")).attrs(attrs).playstyles(Map.of())
                .roleFamiliarity(Map.of()).gender(0).build();
    }

    @Test
    void defaultsAreTheDecision() {
        assertThat(growth.config().getAcademyMaxGain()).isEqualTo(5);
        assertThat(growth.config().getFirstTeamMaxGain()).isEqualTo(8);
    }

    @Test
    void anAcademyPlayerLikeHargreavesGrowsAtMostFiveInASeason() {
        FitPlayer hargreaves = player(15, 53, 92);
        GrowthProjector.Projection academy = growth.project(hargreaves, Stage.ACADEMY);
        assertThat(academy.projectedRating()).as("39 × 0,55 ≈ +21 yerine +5").isEqualTo(58);
        assertThat(academy.grown().getAttrs().get(Attr.SHORT_PASSING)).isEqualTo(60);
        assertThat(projector.project(hargreaves, plans.general(), 1, 1).overall()).isEqualTo(58);
    }

    @Test
    void aFirstTeamNineteenYearOldGrowsAtMostEight() {
        FitPlayer young = player(19, 60, 90);
        assertThat(growth.project(young).projectedRating()).as("30 × 0,55 ≈ +17 yerine +8").isEqualTo(68);
        assertThat(growth.project(young, Stage.FIRST_TEAM).projectedRating()).isEqualTo(68);
        assertThat(growth.project(player(19, 70, 74)).projectedRating()).as("tavan POT'u aşmaz, küçük kazanç aynen").isEqualTo(72);
    }

    @Test
    void twoSeasonsAcademyThenFirstTeam() {
        FitPlayer hargreaves = player(15, 53, 92);
        assertThat(projector.project(hargreaves, plans.general(), 2, 1).overall()).as("1. sezon +5 (altyapı), 2. sezon +8 (A takım)").isEqualTo(66);
        assertThat(projector.project(hargreaves, plans.general(), 2, 2).overall()).as("iki sezon altyapı").isEqualTo(63);
        assertThat(projector.project(hargreaves, plans.general(), 2).overall()).as("varsayılan A takım").isEqualTo(69);
    }

    @Test
    void aPlanStillOnlyChangesTheDistributionUnderTheCap() {
        FitPlayer hargreaves = player(15, 53, 92);
        DevelopmentPlans.Plan role = plans.rolePlans("CM").get(0);
        assertThat(projector.project(hargreaves, role, 1, 1).overall()).isEqualTo(projector.project(hargreaves, plans.general(), 1, 1).overall());
    }
}
