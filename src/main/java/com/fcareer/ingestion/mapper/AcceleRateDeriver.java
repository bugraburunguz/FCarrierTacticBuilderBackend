package com.fcareer.ingestion.mapper;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AcceleRateDeriver {

    private static final int WOMEN = 1;

    private final FitEngineProperties properties;

    public Optional<AcceleRate> derive(Integer heightCm, int gender, Map<Attr, Integer> attrs) {
        if (Objects.isNull(heightCm) || heightCm <= 0) {
            return Optional.empty();
        }
        FitEngineProperties.Accelerate config = properties.getAccelerate();
        boolean women = gender == WOMEN;
        int agility = attrs.getOrDefault(Attr.AGILITY, 0);
        int strength = attrs.getOrDefault(Attr.STRENGTH, 0);
        int acceleration = attrs.getOrDefault(Attr.ACCELERATION, 0);
        int explosiveMaxHeight = women ? config.getExplosiveMaxHeightWomen() : config.getExplosiveMaxHeight();
        int lengthyMinHeight = women ? config.getLengthyMinHeightWomen() : config.getLengthyMinHeight();
        if (heightCm <= explosiveMaxHeight && agility >= config.getExplosiveMinAgility()
                && agility - strength >= config.getExplosiveMinAgilityMinusStrength()
                && acceleration >= config.getExplosiveMinAcceleration()) {
            return Optional.of(AcceleRate.EXPLOSIVE);
        }
        if (heightCm >= lengthyMinHeight && strength >= config.getLengthyMinStrength()
                && agility <= config.getLengthyMaxAgility()
                && strength - agility >= config.getLengthyMinStrengthMinusAgility()
                && acceleration >= config.getLengthyMinAcceleration()) {
            return Optional.of(AcceleRate.LENGTHY);
        }
        return Optional.of(AcceleRate.CONTROLLED);
    }
}
