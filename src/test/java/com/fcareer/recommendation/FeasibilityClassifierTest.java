package com.fcareer.recommendation;

import com.fcareer.recommendation.model.response.Acquisition;
import com.fcareer.recommendation.model.response.Feasibility;
import com.fcareer.recommendation.service.FeasibilityClassifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityClassifierTest {

    @Test
    void worldClassStarFarAboveBudgetAndTierIsUnrealistic() {
        assertThat(FeasibilityClassifier.classify(Acquisition.LOAN, false, 5000.0, 25)).isEqualTo(Feasibility.UNREALISTIC);
        assertThat(FeasibilityClassifier.classify(Acquisition.BUY, false, Double.MAX_VALUE, 20)).isEqualTo(Feasibility.UNREALISTIC);
    }

    @Test
    void affordableInBandPlayerNearSquadLevelIsRealistic() {
        assertThat(FeasibilityClassifier.classify(Acquisition.BUY, true, 0.4, 3)).isEqualTo(Feasibility.REALISTIC);
        assertThat(FeasibilityClassifier.classify(Acquisition.BUY, true, 0.9, 7)).isEqualTo(Feasibility.REALISTIC);
    }

    @Test
    void stretchTargetsAreAmbitious() {
        assertThat(FeasibilityClassifier.classify(Acquisition.LOAN, false, 3.0, 6)).isEqualTo(Feasibility.AMBITIOUS);
        assertThat(FeasibilityClassifier.classify(Acquisition.BUY, true, 2.0, 11)).isEqualTo(Feasibility.AMBITIOUS);
    }

    @Test
    void freeAgentsAreRealisticUnlessFarAboveTheSquad() {
        assertThat(FeasibilityClassifier.classify(Acquisition.FREE, false, null, 6)).isEqualTo(Feasibility.REALISTIC);
        assertThat(FeasibilityClassifier.classify(Acquisition.FREE, false, null, 14)).isEqualTo(Feasibility.AMBITIOUS);
    }
}
