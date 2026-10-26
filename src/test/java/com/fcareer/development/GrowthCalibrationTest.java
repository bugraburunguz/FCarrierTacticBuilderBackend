package com.fcareer.development;

import com.fcareer.development.calibration.GrowthCalibration;
import com.fcareer.development.calibration.GrowthCalibration.Calibration;
import com.fcareer.development.calibration.GrowthCalibration.SnapshotPlayer;
import com.fcareer.fitengine.model.Attr;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * FIT-3 madde 10: ardışık iki IMPORT anlık görüntüsündeki gerçek attribute artışları. Yalnız rapor (katsayılar otomatik değişmez);
 * iki görüntü yoksa "veri yok".
 */
class GrowthCalibrationTest {

    private static SnapshotPlayer p(long id, int age, int ovr, int base, Map<Attr, Integer> bumps) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, base + bumps.getOrDefault(a, 0));
        }
        return new SnapshotPlayer(id, age, ovr, attrs);
    }

    @Test
    void measuresRealAttributeGainsBetweenTwoImports() {
        List<SnapshotPlayer> before = List.of(p(1, 19, 60, 60, Map.of()), p(2, 25, 70, 70, Map.of()), p(3, 18, 55, 55, Map.of()));
        List<SnapshotPlayer> after = List.of(
                p(1, 19, 64, 60, Map.of(Attr.FINISHING, 6, Attr.POSITIONING, 4, Attr.SPRINT_SPEED, 1)),
                p(2, 25, 70, 70, Map.of()));
        Calibration c = GrowthCalibration.summarize(before, after, 0.3);
        assertThat(c.status()).isEqualTo("DATA");
        assertThat(c.players()).as("iki görüntüde de olan oyuncular").isEqualTo(2);
        assertThat(c.meanOverallDelta()).isEqualTo(2.0);
        assertThat(c.attributes().get(0).attr()).as("en çok artan önce").isEqualTo(Attr.FINISHING);
        assertThat(c.attributes().get(0).meanDelta()).isEqualTo(3.0);
        assertThat(c.ageBands()).extracting(GrowthCalibration.AgeBand::label).contains("≤19", "24-25");
        assertThat(c.hardToTrainRatio()).as("hız grubu artışı / diğerleri").isNotNull();
        assertThat(c.modelHardToTrain()).isCloseTo(0.3, within(1e-9));
        assertThat(c.growingPlayers()).as("artışı olan oyuncu").isEqualTo(1);
    }

    private static SnapshotPlayer t(long id, int age, int ovr, Integer team) {
        return new SnapshotPlayer(id, age, ovr, Map.of(), team);
    }

    /**
     * GROWTH-CAL kararı: yaşa ve altyapı/A takım ayrımına göre gerçek OVR artışı ve önerilen tavan (sezona göre ölçekli 90. yüzdelik,
     * yuvarlanmış). Config otomatik değişmez; rapor config'teki tavanı da yazar.
     */
    @Test
    void reportsRealGainsAndASuggestedCapByStageAndAge() {
        List<SnapshotPlayer> before = List.of(t(1, 15, 50, 99), t(2, 15, 52, 99), t(3, 15, 54, 99), t(4, 19, 60, 7), t(5, 19, 62, 7), t(6, 19, 64, 7), t(7, 30, 80, 7));
        List<SnapshotPlayer> after = List.of(t(1, 16, 53, 99), t(2, 16, 56, 99), t(3, 16, 59, 7), t(4, 20, 66, 7), t(5, 20, 70, 7), t(6, 20, 74, 7), t(7, 31, 79, 7));
        List<GrowthCalibration.StageBand> bands = GrowthCalibration.stageBands(before, after, 99, 365L, 5, 8);
        GrowthCalibration.StageBand academy = bands.stream().filter((b) -> b.stage().equals("ALTYAPI")).findFirst().orElseThrow();
        assertThat(academy.label()).isEqualTo("≤15");
        assertThat(academy.players()).as("altyapı = önceki görüntüde altyapı takımında").isEqualTo(3);
        assertThat(academy.meanDelta()).isEqualTo(4.0);
        assertThat(academy.maxDelta()).isEqualTo(5);
        assertThat(academy.suggestedCap()).isEqualTo(5);
        assertThat(academy.configCap()).isEqualTo(5);
        GrowthCalibration.StageBand first = bands.stream().filter((b) -> b.stage().equals("A_TAKIM") && b.label().equals("18-19")).findFirst().orElseThrow();
        assertThat(first.meanDelta()).isEqualTo(8.0);
        assertThat(first.suggestedCap()).as("90. yüzdelik +10").isEqualTo(10);
        assertThat(first.configCap()).isEqualTo(8);
        assertThat(bands).as("yalnız gelişim yaşları (≤ 23)").noneMatch((b) -> b.label().startsWith("30"));
        // Aralık yarım sezonsa artış sezona ölçeklenir (×2).
        GrowthCalibration.StageBand half = GrowthCalibration.stageBands(before, after, 99, 182L, 5, 8).stream()
                .filter((b) -> b.stage().equals("ALTYAPI")).findFirst().orElseThrow();
        assertThat(half.suggestedCap()).isEqualTo(10);
        // Oyun tarihi bilinmiyorsa ölçeksiz.
        assertThat(GrowthCalibration.stageBands(before, after, 99, null, 5, 8).get(0).suggestedCap()).isEqualTo(5);
    }

    /**
     * GT-a madde 4: yaş grubuna göre gerçek düşüş — OVR ve attribute grubu (fiziksel, teknik, zihinsel) ortalaması, sezona ölçekli; modeldeki başlangıç
     * yaşlarıyla kıyas için. Değerler otomatik değişmez.
     */
    @Test
    void reportsRealDeclineByAgeGroupAndAttributeGroup() {
        List<SnapshotPlayer> before = List.of(p(1, 33, 80, 70, Map.of()), p(2, 34, 78, 70, Map.of()), p(3, 26, 75, 70, Map.of()));
        List<SnapshotPlayer> after = List.of(
                p(1, 34, 78, 70, Map.of(Attr.SPRINT_SPEED, -4, Attr.STAMINA, -2, Attr.SHORT_PASSING, -1)),
                p(2, 35, 76, 70, Map.of(Attr.SPRINT_SPEED, -2, Attr.STAMINA, -4, Attr.SHORT_PASSING, -1)),
                p(3, 27, 75, 70, Map.of()));
        List<GrowthCalibration.DeclineBand> bands = GrowthCalibration.declineBands(before, after, 365L);
        GrowthCalibration.DeclineBand old = bands.stream().filter((b) -> b.label().equals("32-33")).findFirst().orElseThrow();
        assertThat(old.players()).isEqualTo(1);
        assertThat(old.meanOverallDelta()).isEqualTo(-2.0);
        GrowthCalibration.DeclineBand older = bands.stream().filter((b) -> b.label().equals("34+")).findFirst().orElseThrow();
        assertThat(older.meanOverallDelta()).isEqualTo(-2.0);
        assertThat(older.physical()).as("fiziksel 7 attribute: (−2 −4) / 7").isCloseTo(-0.86, within(0.01));
        assertThat(older.technical()).isLessThan(0);
        assertThat(older.mental()).isEqualTo(0.0);
        assertThat(bands).extracting(GrowthCalibration.DeclineBand::label).contains("24-29");
        assertThat(bands).extracting(GrowthCalibration.DeclineBand::label).doesNotContain("30-31");
    }

    @Test
    void lessThanTwoImportsMeansNoData() {
        Calibration none = GrowthCalibration.noData();
        assertThat(none.status()).isEqualTo("NO_DATA");
        assertThat(none.message()).isEqualTo("veri yok: kariyer içinden en az iki içe aktarma (attribute'lu) gerekir");
    }
}
