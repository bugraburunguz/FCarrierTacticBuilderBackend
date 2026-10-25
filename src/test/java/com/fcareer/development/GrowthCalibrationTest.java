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

    @Test
    void lessThanTwoImportsMeansNoData() {
        Calibration none = GrowthCalibration.noData();
        assertThat(none.status()).isEqualTo("NO_DATA");
        assertThat(none.message()).isEqualTo("veri yok: kariyer içinden en az iki içe aktarma (attribute'lu) gerekir");
    }
}
