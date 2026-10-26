package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.service.PositionOverallFixture;
import com.fcareer.catalog.service.PositionOverallService;
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

    private static final PositionOverallService OVERALL = PositionOverallFixture.loaded(new ObjectMapper());
    private final GrowthProjector projector = new GrowthProjector(new GrowthProjector.Config(), OVERALL);

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
        // 29: büyüme çarpanı küçük (boşluk 2 → kazanç 0), düşüş henüz yok (fiziksel 30'da başlar).
        GrowthProjector.Projection p = projector.project(player(29, 78, 80));
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
        GrowthProjector.Projection p = projector.project(player(30, 80, 80));
        assertThat(p.grown().getAttrs().get(Attr.SPRINT_SPEED)).isLessThan(60);
        assertThat(p.grown().getAttrs().get(Attr.STAMINA)).isLessThan(60);
        assertThat(p.grown().getAttrs().get(Attr.SHORT_PASSING)).as("teknik daha geç").isEqualTo(60);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isEqualTo(60);
        // Kart OVR attribute'lardan: ST formülünde fiziksel ağırlık küçük, 30'da 1 puanlık fiziksel kayıp OVR'yi düşürmeyebilir; hiç artmaz.
        assertThat(p.projectedRating()).isLessThanOrEqualTo(80);
        assertThat(projector.project(player(33, 80, 80)).projectedRating()).isLessThan(80);
    }

    /**
     * GT-a düzeltmesi (verified:false, kaynak: Buğra oyun tecrübesi, ölçülmedi): attribute grupları sırayla düşer — fiziksel 30, teknik (pas, dribling,
     * şut) 32, zihinsel ve bitiricilik en geç 34. Başlangıç yaşları config'te.
     */
    @Test
    void attributeGroupsStartDecliningAtTheirOwnAge() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        assertThat(config.getDeclineFromAge()).isEqualTo(30);
        assertThat(config.getTechnicalDeclineFromAge()).isEqualTo(32);
        assertThat(config.getMentalDeclineFromAge()).isEqualTo(34);
        assertThat(projector.project(player(29, 80, 80)).projectedRating()).as("29'da düşüş yok").isEqualTo(80);
        Map<Attr, Integer> at32 = projector.project(player(32, 80, 80)).grown().getAttrs();
        assertThat(at32.get(Attr.SHORT_PASSING)).as("teknik 32'de başlar").isLessThan(60);
        assertThat(at32.get(Attr.DRIBBLING)).isLessThan(60);
        assertThat(at32.get(Attr.SHOT_POWER)).isLessThan(60);
        assertThat(at32.get(Attr.FINISHING)).as("bitiricilik en geç").isEqualTo(60);
        assertThat(at32.get(Attr.VISION)).isEqualTo(60);
        Map<Attr, Integer> at34 = projector.project(player(34, 80, 80)).grown().getAttrs();
        assertThat(at34.get(Attr.FINISHING)).isLessThan(60);
        assertThat(at34.get(Attr.POSITIONING)).isLessThan(60);
        assertThat(60 - at34.get(Attr.SPRINT_SPEED)).as("önce başlayan grup daha çok düşer").isGreaterThan(60 - at34.get(Attr.FINISHING));
        // Sonraki grup başladıkça OVR kaybı artar.
        assertThat(80 - projector.project(player(34, 80, 80)).projectedRating()).isGreaterThan(80 - projector.project(player(30, 80, 80)).projectedRating());
        // Başlangıç yaşı config'ten.
        GrowthProjector.Config early = new GrowthProjector.Config();
        early.setTechnicalDeclineFromAge(30);
        assertThat(new GrowthProjector(early, OVERALL).project(player(30, 80, 80)).grown().getAttrs().get(Attr.SHORT_PASSING)).isLessThan(60);
    }

    /**
     * GT-a düzeltmesi: yaşa göre kaybedilen toplam attribute puanı Dengeli ile aynı; plan bu kaybı plan attribute'larından (planDeclineDelay yıl geç,
     * planDeclineFactor kadar yavaş) plan dışı attribute'lara kaydırır. Kart OVR zorlanmaz, attribute'lardan (ana mevki formülü) hesaplanır.
     */
    @Test
    void aPlanShiftsTheLossToOtherAttributesKeepingTheTotal() {
        GrowthProjector.Config config = new GrowthProjector.Config();
        assertThat(config.getPlanDeclineFactor()).isEqualTo(0.5);
        assertThat(config.getPlanDeclineDelay()).isEqualTo(2);
        List<Attr> plan = List.of(Attr.FINISHING, Attr.SHORT_PASSING);
        FitPlayer old = player(38, 80, 80);
        Map<Attr, Integer> balanced = projector.project(old).grown().getAttrs();
        Map<Attr, Integer> withPlan = projector.project(old, plan).grown().getAttrs();
        assertThat(60 - withPlan.get(Attr.SHORT_PASSING)).as("plan attribute'u yavaş düşer").isLessThan(60 - balanced.get(Attr.SHORT_PASSING));
        assertThat(60 - withPlan.get(Attr.FINISHING)).isLessThan(60 - balanced.get(Attr.FINISHING));
        assertThat(60 - withPlan.get(Attr.DRIBBLING)).as("plan dışı daha çok düşer").isGreaterThanOrEqualTo(60 - balanced.get(Attr.DRIBBLING));
        assertThat(total(withPlan)).as("toplam kayıp aynı").isEqualTo(total(balanced));
        // Çarpan: 38 yaşında bitiricilik (zihinsel 34 + gecikme 2 = 36) 1,8 puan yerine 0,5 × 1,8.
        assertThat(projector.declines(old, plan).get(Attr.FINISHING)).isCloseTo(0.9, org.assertj.core.api.Assertions.within(1e-9));
        // Saha oyuncusunda kaleci attribute'ları düşmez, kayıp onlara kaymaz.
        assertThat(withPlan.get(Attr.GK_DIVING)).isEqualTo(60);
        assertThat(balanced.get(Attr.GK_REFLEXES)).isEqualTo(60);
        // Gecikme: 32 yaşında teknik grup başladı ama plandaki pas (32 + 2 = 34'te başlar) henüz düşmez.
        assertThat(projector.project(player(32, 80, 80), plan).grown().getAttrs().get(Attr.SHORT_PASSING)).isEqualTo(60);
        // Plan bütün düşen attribute'ları kapsasa da toplam aynı: kayıp plan dışına kayar.
        List<Attr> physical = List.copyOf(GrowthProjector.PHYSICAL);
        assertThat(total(projector.project(player(30, 80, 80), physical).grown().getAttrs())).isEqualTo(total(projector.project(player(30, 80, 80)).grown().getAttrs()));
    }

    /**
     * Kart OVR attribute'lardan: eski OVR − ana mevki formülündeki ağırlıklı kayıp. Mevkide ağır attribute'ları koruyan plan (ST'de bitiricilik, pozisyon
     * alma, şut gücü) OVR'yi daha az düşürür; mevkiyle ilgisiz attribute'ları koruyan plan daha çok. Düşüş artışa dönmez.
     */
    @Test
    void cardOverallComesFromTheAttributesSoAPositionPlanLosesLess() {
        FitPlayer old = player(35, 80, 80);
        GrowthProjector.Projection balanced = projector.project(old);
        Map<Attr, Double> w = OVERALL.weights("ST");
        double weighted = w.entrySet().stream().mapToDouble((e) -> e.getValue() * (60 - balanced.grown().getAttrs().get(e.getKey()))).sum();
        assertThat(balanced.projectedRating()).as("80 − ağırlıklı kayıp").isEqualTo(80 - (int) Math.round(weighted));
        GrowthProjector.Projection striker = projector.project(old, List.of(Attr.FINISHING, Attr.POSITIONING, Attr.SHOT_POWER, Attr.BALL_CONTROL, Attr.SPRINT_SPEED));
        GrowthProjector.Projection defender = projector.project(old, List.of(Attr.STANDING_TACKLE, Attr.SLIDING_TACKLE, Attr.DEF_AWARENESS, Attr.INTERCEPTIONS, Attr.HEADING));
        assertThat(striker.projectedRating()).as("mevkide ağır attribute'ları koruyan plan daha az düşer").isGreaterThan(balanced.projectedRating());
        assertThat(defender.projectedRating()).as("ilgisiz attribute'ları koruyan plan daha çok düşer").isLessThanOrEqualTo(balanced.projectedRating());
        for (GrowthProjector.Projection p : List.of(balanced, striker, defender)) {
            assertThat(p.projectedRating()).as("düşüş artışa dönmez").isLessThanOrEqualTo(80);
            assertThat(p.grown().getAttrs().values()).allMatch((v) -> v <= 60);
        }
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
