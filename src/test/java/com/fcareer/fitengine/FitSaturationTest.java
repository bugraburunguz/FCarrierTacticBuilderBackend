package com.fcareer.fitengine;

import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5b: currentFit sert 100 kırpması yerine yumuşak doygunluk kullanır (0-85 aralığı ±1, üst uçta kesin sıra, 100'e ulaşmaz). */
class FitSaturationTest {

    @Test
    void scoresUpToEightyFiveMoveByAtMostOnePoint() {
        for (double x = 0; x <= 85; x += 0.5) {
            assertThat(Math.abs(FitEngineV3.saturate(x) - x)).as("x=" + x).isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    void theTopEndStaysStrictlyIncreasingAndBelowOneHundred() {
        double previous = FitEngineV3.saturate(85);
        for (double x = 86; x <= 160; x += 1) {
            double y = FitEngineV3.saturate(x);
            assertThat(y).as("x=" + x).isGreaterThan(previous).isLessThan(100.0);
            previous = y;
        }
    }

    @Test
    void negativeTotalsClampToZero() {
        assertThat(FitEngineV3.saturate(-12)).isEqualTo(0.0);
    }
}
