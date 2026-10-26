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
 * E3-6 futureFit(+1 sezon) büyüme modeli. projectedRating tanımı: sezon sonu beklenen OVR = min(POT, OVR + round(kalan boşluk × yaş çarpanı)).
 * Attribute artışı: sezon OVR kazancı × trainability (çevik/hız attribute'ları 0,3×, diğerleri 1,0×), 99'da kırpılır.
 */
class GrowthProjectorTest {

    private final GrowthProjector projector = new GrowthProjector(new GrowthProjector.Config());

    private static FitPlayer player(int age, int ovr, int pot) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, 60);
        }
        return FitPlayer.builder().id(1L).name("p").age(age).overall(ovr).potential(pot).positions(List.of("ST")).attrs(attrs).playstyles(Map.of()).gender(0).build();
    }

    @Test
    void aYoungPlayerWithHeadroomGrowsAndItIsReflectedInProjectedRatingAndAttributes() {
        GrowthProjector.Projection p = projector.project(player(19, 70, 86));
        assertThat(p.projectedRating()).isBetween(75, 79);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isGreaterThan(60);
        assertThat(p.grown().getAttrs().get(Attr.SPRINT_SPEED)).as("zor gelişen attribute daha az artar").isLessThan(p.grown().getAttrs().get(Attr.FINISHING));
    }

    @Test
    void ageStopsGrowth() {
        GrowthProjector.Projection p = projector.project(player(31, 78, 80));
        assertThat(p.projectedRating()).isEqualTo(78);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isEqualTo(60);
    }

    @Test
    void projectedRatingNeverExceedsPotentialOrNinetyNine() {
        assertThat(projector.project(player(18, 84, 85)).projectedRating()).isLessThanOrEqualTo(85);
        assertThat(projector.project(player(18, 98, 99)).grown().getAttrs().values()).allMatch((v) -> v <= 99);
    }

    @Test
    void anUnknownPotentialOrAgeMeansNoGrowthRatherThanAGuess() {
        FitPlayer noPotential = FitPlayer.builder().id(2L).age(20).overall(70).positions(List.of("ST")).attrs(player(20, 70, 80).getAttrs()).playstyles(Map.of()).build();
        assertThat(projector.project(noPotential).projectedRating()).isEqualTo(70);
    }

    @Test
    void anOldPlayerLosesPhysicalAttributesAndRatingFirst() {
        GrowthProjector.Projection p = projector.project(player(32, 80, 80));
        assertThat(p.grown().getAttrs().get(Attr.SPRINT_SPEED)).isLessThan(60);
        assertThat(p.grown().getAttrs().get(Attr.STAMINA)).isLessThan(60);
        assertThat(p.grown().getAttrs().get(Attr.SHORT_PASSING)).as("teknik daha geç").isEqualTo(60);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isEqualTo(60);
        assertThat(p.projectedRating()).isLessThan(80);
    }

    /**
     * GT-a madde 2 (verified:false, kaynak: Buğra oyun tecrübesi, ölçülmedi): attribute grupları sırayla düşer — fiziksel (32), teknik (pas, dribling,
     * şut; 33), zihinsel ve bitiricilik en geç (35). Başlangıç yaşları config'te.
     */
    @Test
    void attributeGroupsStartDecliningAtTheirOwnAge() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        assertThat(config.getDeclineFromAge()).isEqualTo(32);
        assertThat(config.getTechnicalDeclineFromAge()).isEqualTo(33);
        assertThat(config.getMentalDeclineFromAge()).isEqualTo(35);
        Map<Attr, Integer> at33 = projector.project(player(33, 80, 80)).grown().getAttrs();
        assertThat(at33.get(Attr.SHORT_PASSING)).as("teknik 33'te başlar").isLessThan(60);
        assertThat(at33.get(Attr.DRIBBLING)).isLessThan(60);
        assertThat(at33.get(Attr.SHOT_POWER)).isLessThan(60);
        assertThat(at33.get(Attr.FINISHING)).as("bitiricilik en geç").isEqualTo(60);
        assertThat(at33.get(Attr.VISION)).isEqualTo(60);
        Map<Attr, Integer> at35 = projector.project(player(35, 80, 80)).grown().getAttrs();
        assertThat(at35.get(Attr.FINISHING)).isLessThan(60);
        assertThat(at35.get(Attr.POSITIONING)).isLessThan(60);
        assertThat(60 - at35.get(Attr.SPRINT_SPEED)).as("önce başlayan grup daha çok düşer").isGreaterThan(60 - at35.get(Attr.FINISHING));
        // Sonraki grup başladıkça OVR kaybı artar.
        assertThat(80 - projector.project(player(35, 80, 80)).projectedRating()).isGreaterThan(80 - projector.project(player(32, 80, 80)).projectedRating());
        // Başlangıç yaşı config'ten.
        GrowthProjector.Config early = new GrowthProjector.Config();
        early.setTechnicalDeclineFromAge(32);
        assertThat(new GrowthProjector(early).project(player(32, 80, 80)).grown().getAttrs().get(Attr.SHORT_PASSING)).isLessThan(60);
    }

    /**
     * GT-a madde 2: plan attribute'ları daha geç (planDeclineDelay yıl) ve daha yavaş (planDeclineFactor, 0,5) düşer. Plan dağılımı değiştirir, toplamı
     * değil: toplam düşüş puanı ve kart OVR'si Dengeli ile aynı.
     */
    @Test
    void aPlanSlowsTheDeclineOfItsAttributesWithoutChangingTheTotal() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        assertThat(config.getPlanDeclineFactor()).isEqualTo(0.5);
        assertThat(config.getPlanDeclineDelay()).isEqualTo(2);
        List<Attr> plan = List.of(Attr.FINISHING, Attr.SHORT_PASSING);
        FitPlayer old = player(38, 80, 80);
        Map<Attr, Integer> balanced = projector.project(old).grown().getAttrs();
        GrowthProjector.Projection planned = projector.project(old, plan);
        Map<Attr, Integer> withPlan = planned.grown().getAttrs();
        assertThat(60 - withPlan.get(Attr.SHORT_PASSING)).as("plan attribute'u yavaş düşer").isLessThan(60 - balanced.get(Attr.SHORT_PASSING));
        assertThat(60 - withPlan.get(Attr.FINISHING)).isLessThan(60 - balanced.get(Attr.FINISHING));
        assertThat(60 - withPlan.get(Attr.DRIBBLING)).as("plan dışı daha çok düşer").isGreaterThanOrEqualTo(60 - balanced.get(Attr.DRIBBLING));
        assertThat(total(withPlan)).as("toplam düşüş aynı").isEqualTo(total(balanced));
        // Çarpan: 38 yaşında bitiricilik (zihinsel 35 + gecikme 2 = 37) 1,4 puan yerine 0,5 × 1,4.
        assertThat(projector.declines(old, plan).get(Attr.FINISHING)).isCloseTo(0.7, org.assertj.core.api.Assertions.within(1e-9));
        // Saha oyuncusunda kaleci attribute'ları düşmez, korunan puan onlara gitmez.
        assertThat(withPlan.get(Attr.GK_DIVING)).isEqualTo(60);
        assertThat(balanced.get(Attr.GK_REFLEXES)).isEqualTo(60);
        assertThat(planned.projectedRating()).as("kart OVR yaş kuralıyla").isEqualTo(projector.project(old).projectedRating());
        // Gecikme: 33 yaşında teknik grup başladı ama plandaki pas (33 + 2 = 35'te başlar) henüz düşmez.
        assertThat(projector.project(player(33, 80, 80), plan).grown().getAttrs().get(Attr.SHORT_PASSING)).isEqualTo(60);
    }

    private static int total(Map<Attr, Integer> attrs) {
        return attrs.values().stream().mapToInt((v) -> 60 - v).sum();
    }

    @Test
    void declineGrowsWithAge() {
        int at33 = projector.project(player(33, 80, 80)).grown().getAttrs().get(Attr.SPRINT_SPEED);
        int at38 = projector.project(player(38, 80, 80)).grown().getAttrs().get(Attr.SPRINT_SPEED);
        assertThat(at38).isLessThan(at33);
    }
}
