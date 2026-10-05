package com.fcareer.ingestion.mapper;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AcceleRateDeriver {

    private final FitEngineProperties properties;

    // Eşikler CONFIG (accelerate_thresholds) — FC27'de teyit edilecek.
    public AcceleRate derive(Integer heightCm, Map<Attr, Integer> attrs) {
        if (Objects.isNull(heightCm)) {
            return AcceleRate.CONTROLLED;
        }
        FitEngineProperties.Accelerate config = properties.getAccelerate();
        int agility = attrs.getOrDefault(Attr.AGILITY, 0);
        int strength = attrs.getOrDefault(Attr.STRENGTH, 0);
        if (heightCm <= config.getExplosiveMaxHeight() && agility >= config.getExplosiveMinAgility()
                && agility - strength >= config.getExplosiveMinAgilityMinusStrength()) {
            return AcceleRate.EXPLOSIVE;
        }
        if (heightCm >= config.getLengthyMinHeight() && strength >= config.getLengthyMinStrength()
                && strength - agility >= config.getLengthyMinStrengthMinusAgility()) {
            return AcceleRate.LENGTHY;
        }
        return AcceleRate.CONTROLLED;
    }
}
