package com.fcareer.mlclient;

import com.fcareer.mlclient.service.ModeledValueFormula;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeledValueFormulaTest {

    @Test
    void valueGrowsWithOverall() {
        long low = ModeledValueFormula.estimate(60, 60, 27, 1);
        long mid = ModeledValueFormula.estimate(75, 75, 27, 1);
        long high = ModeledValueFormula.estimate(90, 90, 27, 1);
        assertThat(low).isLessThan(mid);
        assertThat(mid).isLessThan(high);
        assertThat(high).isBetween(100_000_000L, 200_000_000L);
    }

    @Test
    void lowerLeaguesAreWorthLess() {
        assertThat(ModeledValueFormula.estimate(70, 70, 26, 4)).isLessThan(ModeledValueFormula.estimate(70, 70, 26, 1));
    }

    @Test
    void youngHighPotentialIsLiftedTowardsPotential() {
        long prospect = ModeledValueFormula.estimate(60, 80, 19, 1);
        long sameOverallVeteran = ModeledValueFormula.estimate(60, 60, 27, 1);
        assertThat(prospect).isGreaterThan(sameOverallVeteran * 5);
    }

    @Test
    void valueDropsAfterPrimeAndNeverVanishes() {
        long prime = ModeledValueFormula.estimate(88, 88, 28, 1);
        long old = ModeledValueFormula.estimate(88, 88, 38, 1);
        assertThat(old).isLessThan(prime / 4);
        assertThat(old).isPositive();
    }

    @Test
    void unknownTierIsBetweenTopAndLowTiers() {
        long unknown = ModeledValueFormula.estimate(70, 70, 26, null);
        assertThat(unknown).isLessThan(ModeledValueFormula.estimate(70, 70, 26, 1));
        assertThat(unknown).isGreaterThan(ModeledValueFormula.estimate(70, 70, 26, 4));
    }
}
