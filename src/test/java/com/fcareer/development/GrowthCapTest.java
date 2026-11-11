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
 * A takımda da en çok +5 (FCA-20; kazanç yaşa göre mutlak puan). İki sezonluk tahminde altyapı oyuncusu ikinci sezonda A takımda sayılır. Tavan plan
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
        assertThat(growth.config().getFirstTeamMaxGain()).as("FCA-20: oynamayan genç A takımda altyapıdan hızlı gelişmedi").isEqualTo(5);
    }

    @Test
    void anAcademyPlayerLikeHargreavesGrowsAtMostFiveInASeason() {
        FitPlayer hargreaves = player(15, 53, 92);
        GrowthProjector.Projection academy = growth.project(hargreaves, Stage.ACADEMY);
        assertThat(academy.projectedRating()).as("39 puan fark olsa da +5").isEqualTo(58);
        assertThat(academy.grown().getAttrs().get(Attr.SHORT_PASSING)).isEqualTo(60);
        assertThat(projector.project(hargreaves, plans.general(), 1, 1).overall()).isEqualTo(58);
    }

    @Test
    void aFirstTeamNineteenYearOldGrowsByAgeNotByHeadroom() {
        FitPlayer young = player(19, 60, 90);
        assertThat(growth.project(young).projectedRating()).as("FCA-20: 30 puan fark olsa da 19 yaşta +3").isEqualTo(63);
        assertThat(growth.project(young, Stage.FIRST_TEAM).projectedRating()).isEqualTo(63);
        assertThat(growth.project(player(19, 70, 72)).projectedRating()).as("POT'u aşmaz").isEqualTo(72);
    }

    @Test
    void twoSeasonsAcademyThenFirstTeam() {
        FitPlayer hargreaves = player(15, 53, 92);
        assertThat(projector.project(hargreaves, plans.general(), 2, 1).overall()).as("1. sezon +5 (altyapı), 2. sezon +5 (A takım)").isEqualTo(63);
        assertThat(projector.project(hargreaves, plans.general(), 2, 2).overall()).as("iki sezon altyapı").isEqualTo(63);
        assertThat(projector.project(hargreaves, plans.general(), 2).overall()).as("varsayılan A takım").isEqualTo(63);
    }

    @Test
    void eachStageUsesItsOwnCap() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        config.setAcademyMaxGain(2);
        config.setFirstTeamMaxGain(4);
        GrowthProjector capped = new GrowthProjector(config);
        FitPlayer hargreaves = player(15, 53, 92);
        assertThat(capped.project(hargreaves, Stage.ACADEMY).projectedRating()).isEqualTo(55);
        assertThat(capped.project(hargreaves, Stage.FIRST_TEAM).projectedRating()).isEqualTo(57);
        PlanProjector cappedProjector = new PlanProjector(capped, OVERALL);
        assertThat(cappedProjector.project(hargreaves, plans.general(), 2, 1).overall()).as("1. sezon altyapı +2, 2. sezon A takım +4").isEqualTo(59);
    }

    @Test
    void aPlanStillOnlyChangesTheDistributionUnderTheCap() {
        FitPlayer hargreaves = player(15, 53, 92);
        DevelopmentPlans.Plan role = plans.rolePlans("CM").get(0);
        assertThat(projector.project(hargreaves, role, 1, 1).overall()).isEqualTo(projector.project(hargreaves, plans.general(), 1, 1).overall());
    }
}
