package com.fcareer.ingestion;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.mapper.AcceleRateDeriver;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AcceleRateDeriverTest {

    private static final int MEN = 0;
    private static final int WOMEN = 1;

    private final AcceleRateDeriver deriver = new AcceleRateDeriver(new FitEngineProperties());

    private Map<Attr, Integer> attrs(int agility, int strength, int acceleration) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.put(Attr.AGILITY, agility);
        attrs.put(Attr.STRENGTH, strength);
        attrs.put(Attr.ACCELERATION, acceleration);
        return attrs;
    }

    @Test
    void unknownHeightYieldsNoArchetypeInsteadOfGuessingControlled() {
        assertThat(deriver.derive(null, MEN, attrs(90, 60, 90))).isEmpty();
        assertThat(deriver.derive(0, MEN, attrs(90, 60, 90))).isEmpty();
    }

    @Test
    void explosiveNeedsLowHeightAgilityLeadAndAcceleration() {
        assertThat(deriver.derive(178, MEN, attrs(93, 77, 97))).contains(AcceleRate.EXPLOSIVE);
        assertThat(deriver.derive(178, MEN, attrs(93, 77, 79))).contains(AcceleRate.CONTROLLED);
        assertThat(deriver.derive(185, MEN, attrs(93, 77, 97))).contains(AcceleRate.CONTROLLED);
        assertThat(deriver.derive(178, MEN, attrs(80, 75, 90))).contains(AcceleRate.CONTROLLED);
    }

    @Test
    void lengthyNeedsHeightStrengthLeadAndMinimumAcceleration() {
        assertThat(deriver.derive(194, MEN, attrs(66, 93, 81))).contains(AcceleRate.LENGTHY);
        assertThat(deriver.derive(180, MEN, attrs(66, 93, 81))).contains(AcceleRate.CONTROLLED);
        assertThat(deriver.derive(194, MEN, attrs(90, 93, 81))).contains(AcceleRate.CONTROLLED);
        assertThat(deriver.derive(194, MEN, attrs(40, 90, 39))).contains(AcceleRate.CONTROLLED);
    }

    @Test
    void womenUseTheirOwnHeightThresholds() {
        Optional<AcceleRate> woman = deriver.derive(170, WOMEN, attrs(60, 70, 70));
        Optional<AcceleRate> man = deriver.derive(170, MEN, attrs(60, 70, 70));

        assertThat(woman).contains(AcceleRate.LENGTHY);
        assertThat(man).contains(AcceleRate.CONTROLLED);
        assertThat(deriver.derive(160, WOMEN, attrs(90, 60, 90))).contains(AcceleRate.EXPLOSIVE);
        assertThat(deriver.derive(170, WOMEN, attrs(90, 60, 90))).contains(AcceleRate.CONTROLLED);
    }
}
