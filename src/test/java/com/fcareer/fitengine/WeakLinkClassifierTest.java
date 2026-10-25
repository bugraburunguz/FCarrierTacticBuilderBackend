package com.fcareer.fitengine;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.service.WeakLinkClassifier;
import com.fcareer.fitengine.service.WeakLinkClassifier.Slot;
import com.fcareer.fitengine.service.WeakLinkClassifier.WeakClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-1 madde 2: iki eksenli zayıf halka. Güç (currentFit) takımın 11'i içinde alt üçte birde mi, Rol oturması (levelFit) 40'ın altında mı.
 * KALITE_ACIGI: alt üçte bir + oturma ≥ 40 · ROL_UYUMSUZ: oturma < 40 + Güç ≥ takım medyanı · IKISI: ikisi · UYGUN: diğerleri.
 */
class WeakLinkClassifierTest {

    private final WeakLinkClassifier classifier = new WeakLinkClassifier(new FitEngineProperties());

    /** 11 slot: Güç 50, 55, …, 100 (S0 en düşük); oturma verilenler dışında 70. */
    private static List<Slot> team(Map<String, Double> roleFitOverrides) {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String id = "S" + i;
            slots.add(new Slot(id, 50.0 + 5 * i, roleFitOverrides.getOrDefault(id, 70.0)));
        }
        return slots;
    }

    @Test
    void fourClasses() {
        Map<String, WeakClass> classes = classifier.classify(team(Map.of("S0", 30.0, "S1", 55.0, "S9", 20.0)));
        assertThat(classes.get("S0")).as("alt üçte bir + oturma < 40").isEqualTo(WeakClass.IKISI);
        assertThat(classes.get("S1")).as("alt üçte bir + oturma ≥ 40").isEqualTo(WeakClass.KALITE_ACIGI);
        assertThat(classes.get("S9")).as("oturma < 40 + Güç medyan üstü").isEqualTo(WeakClass.ROL_UYUMSUZ);
        assertThat(classes.get("S5")).isEqualTo(WeakClass.UYGUN);
    }

    @Test
    void thresholdBoundaries() {
        // Alt üçte bir = 11'in en düşük 3'ü (S0, S1, S2); S3 değil. Oturma tam 40 → eksik sayılmaz.
        Map<String, WeakClass> classes = classifier.classify(team(Map.of("S2", 40.0, "S3", 70.0, "S5", 39.9, "S4", 39.9)));
        assertThat(classes.get("S2")).isEqualTo(WeakClass.KALITE_ACIGI);
        assertThat(classes.get("S3")).isEqualTo(WeakClass.UYGUN);
        // Medyan = S5'in Güç'ü (75): S5 medyanda → ROL_UYUMSUZ; S4 medyanın altında ve alt üçte birde değil → UYGUN.
        assertThat(classes.get("S5")).isEqualTo(WeakClass.ROL_UYUMSUZ);
        assertThat(classes.get("S4")).isEqualTo(WeakClass.UYGUN);
    }

    @Test
    void unknownRoleFitUsesOnlyTheQualityAxis() {
        List<Slot> slots = new ArrayList<>(team(Map.of()));
        slots.set(0, new Slot("S0", 50.0, null));
        slots.set(10, new Slot("S10", 100.0, null));
        Map<String, WeakClass> classes = classifier.classify(slots);
        assertThat(classes.get("S0")).isEqualTo(WeakClass.KALITE_ACIGI);
        assertThat(classes.get("S10")).isEqualTo(WeakClass.UYGUN);
    }
}
