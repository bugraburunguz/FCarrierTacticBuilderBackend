package com.fcareer.fitengine.service;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** FIT-1 madde 2: iki eksenli zayıf halka sınıfı (saf; eşikler fcareer.fit.weak-link, verified:false). */
@Component
@RequiredArgsConstructor
public class WeakLinkClassifier {

    public enum WeakClass { KALITE_ACIGI, ROL_UYUMSUZ, IKISI, UYGUN }

    /** strength = Güç (currentFit); roleFit = Rol oturması (levelFit), norm yoksa null. */
    public record Slot(String slotId, double strength, Double roleFit) {
    }

    private final FitEngineProperties properties;

    /**
     * KALITE_ACIGI: Güç takımın alt `qualityLowerFraction`'ında (11'de en düşük 3) ve Rol oturması ≥ `roleFitMin`.
     * ROL_UYUMSUZ: oturma < `roleFitMin` ve Güç ≥ takım medyanı. IKISI: alt dilim + oturma < eşik. UYGUN: diğerleri.
     * Oturma bilinmiyorsa (norm yok) yalnız kalite ekseni.
     */
    public Map<String, WeakClass> classify(List<Slot> slots) {
        FitEngineProperties.WeakLink config = properties.getWeakLink();
        List<Slot> byStrength = slots.stream().sorted(Comparator.comparingDouble(Slot::strength).thenComparing(Slot::slotId)).toList();
        int lowCount = (int) Math.floor(slots.size() * config.getQualityLowerFraction());
        Set<String> low = byStrength.stream().limit(lowCount).map(Slot::slotId).collect(Collectors.toSet());
        double median = median(byStrength);
        Map<String, WeakClass> classes = new HashMap<>();
        for (Slot slot : slots) {
            boolean lowQuality = low.contains(slot.slotId());
            boolean misfit = Objects.nonNull(slot.roleFit()) && slot.roleFit() < config.getRoleFitMin();
            WeakClass weakClass = lowQuality && misfit ? WeakClass.IKISI
                    : lowQuality ? WeakClass.KALITE_ACIGI
                    : misfit && slot.strength() >= median ? WeakClass.ROL_UYUMSUZ
                    : WeakClass.UYGUN;
            classes.put(slot.slotId(), weakClass);
        }
        return classes;
    }

    private static double median(List<Slot> sorted) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid).strength() : (sorted.get(mid - 1).strength() + sorted.get(mid).strength()) / 2;
    }
}
