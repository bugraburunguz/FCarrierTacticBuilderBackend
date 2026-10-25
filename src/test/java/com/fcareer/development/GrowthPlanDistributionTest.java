package com.fcareer.development;

import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-3 madde 9: plan farkındalıklı büyüme. Dengeli (plan yok) bugünkü eşit dağılımın aynısı. Mevki/rol planında sezon kazancı bütçesinin
 * `planShare` kadarı (0,7, verified:false) planın attribute'larına, kalanı diğer saha attribute'larına eşit gider. Hız grubu 0,3× ve 99 tavanı aynen.
 */
class GrowthPlanDistributionTest {

    private final GrowthProjector projector = new GrowthProjector(new GrowthProjector.Config());

    private static FitPlayer player(int age, int ovr, int pot, int all) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, all);
        }
        return FitPlayer.builder().id(1L).name("p").age(age).overall(ovr).potential(pot).positions(List.of("CM")).attrs(attrs).playstyles(Map.of()).gender(0).build();
    }

    private static final List<Attr> PLAN = List.of(Attr.FINISHING, Attr.POSITIONING, Attr.LONG_SHOTS, Attr.VISION, Attr.LONG_PASSING, Attr.SHORT_PASSING,
            Attr.BALL_CONTROL, Attr.DRIBBLING);

    @Test
    void noPlanIsExactlyTodaysBalancedGrowth() {
        FitPlayer p = player(19, 70, 86, 60);
        assertThat(projector.project(p, List.of()).grown().getAttrs()).isEqualTo(projector.project(p).grown().getAttrs());
        assertThat(projector.project(p, List.of()).projectedRating()).isEqualTo(projector.project(p).projectedRating());
    }

    @Test
    void planAttributesTakeMostOfTheSeasonBudget() {
        // 23 yaş, 70→76: kazanç round(6 × 0,35) = 2; bütçe 2 × 29 saha attribute'u = 58.
        // Karar: pay 0,45. Plan (8): round(0,45 × 58 / 8) = 3 · diğerleri (21): round(0,55 × 58 / 21) = 2 · hız grubu: round(1,52 × 0,3) = 0 · kaleci: değişmez.
        Map<Attr, Integer> grown = projector.project(player(23, 70, 76, 60), PLAN).grown().getAttrs();
        assertThat(grown.get(Attr.FINISHING)).isEqualTo(63);
        assertThat(grown.get(Attr.DRIBBLING)).isEqualTo(63);
        assertThat(grown.get(Attr.STAMINA)).isEqualTo(62);
        assertThat(grown.get(Attr.SPRINT_SPEED)).isEqualTo(60);
        assertThat(grown.get(Attr.GK_DIVING)).as("saha planı kaleci attribute'una bütçe ayırmaz").isEqualTo(60);
    }

    @Test
    void ninetyNineCapAndPaceGroupRuleStillHold() {
        Map<Attr, Integer> grown = projector.project(player(18, 60, 95, 97), List.of(Attr.FINISHING, Attr.SPRINT_SPEED)).grown().getAttrs();
        assertThat(grown.get(Attr.FINISHING)).isEqualTo(99);
        assertThat(grown.get(Attr.SPRINT_SPEED)).isEqualTo(99);
        Map<Attr, Integer> pace = projector.project(player(23, 70, 76, 60), List.of(Attr.SPRINT_SPEED, Attr.FINISHING)).grown().getAttrs();
        assertThat(pace.get(Attr.SPRINT_SPEED) - 60).as("hız grubu planda da 0,3×").isLessThan(pace.get(Attr.FINISHING) - 60);
    }

    @Test
    void goalkeeperPlanBudgetsOnlyGoalkeepingAttributes() {
        // İnceleme: kaleci planı bütçesi 34 attribute'tan değil yalnız 5 kaleci attribute'undan (yoksa 3 attribute'a ~8× kazanç yığılıyordu).
        // 23 yaş, 70→76: kazanç 2; bütçe 2 × 5 = 10; plan (3): round(0,45 × 10 / 3) = 2 · diğer kaleci (2): round(0,55 × 10 / 2) = 3 · saha: değişmez.
        Map<Attr, Integer> grown = projector.project(player(23, 70, 76, 60), List.of(Attr.GK_DIVING, Attr.GK_HANDLING, Attr.GK_REFLEXES)).grown().getAttrs();
        assertThat(grown.get(Attr.GK_DIVING)).isEqualTo(62);
        assertThat(grown.get(Attr.GK_KICKING)).isEqualTo(63);
        assertThat(grown.get(Attr.FINISHING)).isEqualTo(60);
    }

    @Test
    void planShareIsConfigurable() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        config.setPlanShare(1.0);
        Map<Attr, Integer> grown = new GrowthProjector(config).project(player(23, 70, 76, 60), PLAN).grown().getAttrs();
        assertThat(grown.get(Attr.STAMINA)).as("pay 1,0: plan dışı artmaz").isEqualTo(60);
    }
}
