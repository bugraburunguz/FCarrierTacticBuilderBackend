package com.fcareer.fitengine;

import com.fcareer.fitengine.level.LevelFitCalculator;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5 levelFit: OVR ±3 bandı (<30 oyuncuda genişler, bant kaydedilir) ve yüzdelik hesabı. */
class LevelFitCalculatorTest {

    private static final int MIN_PLAYERS = 30;
    private static final int HALF_WIDTH = 3;
    private static final int MAX_HALF_WIDTH = 10;

    @Test
    void aDenseBandKeepsThePlusMinusThreeWidth() {
        int[] ovrs = IntStream.range(0, 200).map((i) -> 60 + i % 15).sorted().toArray();
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 66, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.lo()).isEqualTo(63);
        assertThat(band.hi()).isEqualTo(69);
        assertThat(band.count()).isGreaterThanOrEqualTo(MIN_PLAYERS);
    }

    @Test
    void aSparseBandWidensUntilThirtyPlayersAndRecordsTheWidth() {
        int[] ovrs = IntStream.concat(IntStream.of(88, 88, 89, 90), IntStream.range(0, 40).map((i) -> 80 + i % 6)).sorted().toArray();
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 90, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.count()).isGreaterThanOrEqualTo(MIN_PLAYERS);
        assertThat(band.lo()).isLessThan(87);
        assertThat(band.hi() - band.lo()).isGreaterThan(2 * HALF_WIDTH);
    }

    @Test
    void theBandNeverWidensBeyondTheMaximumHalfWidth() {
        int[] ovrs = {50, 51, 52};
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 51, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.lo()).isEqualTo(41);
        assertThat(band.hi()).isEqualTo(61);
        assertThat(band.count()).isEqualTo(3);
    }

    @Test
    void quantilesOfAUniformRangeAreTheRangeItself() {
        double[] values = IntStream.rangeClosed(0, 100).asDoubleStream().toArray();
        double[] q = LevelFitCalculator.quantiles(values);
        assertThat(q).hasSize(101);
        assertThat(q[0]).isEqualTo(0.0);
        assertThat(q[50]).isEqualTo(50.0);
        assertThat(q[100]).isEqualTo(100.0);
    }

    @Test
    void percentileIsMonotoneBoundedAndPutsTheMedianAtFifty() {
        double[] q = LevelFitCalculator.quantiles(IntStream.rangeClosed(0, 100).asDoubleStream().toArray());
        assertThat(LevelFitCalculator.percentile(q, 50)).isEqualTo(50.0);
        assertThat(LevelFitCalculator.percentile(q, -5)).isEqualTo(0.0);
        assertThat(LevelFitCalculator.percentile(q, 140)).isEqualTo(100.0);
        assertThat(LevelFitCalculator.percentile(q, 25.5)).isLessThan(LevelFitCalculator.percentile(q, 25.6));
    }

    @Test
    void tiedValuesGetTheMidpointPercentileSoAFlatDistributionIsNotRankedExtreme() {
        double[] flat = new double[40];
        java.util.Arrays.fill(flat, 70.0);
        double[] q = LevelFitCalculator.quantiles(flat);
        assertThat(LevelFitCalculator.percentile(q, 70.0)).isEqualTo(50.0);
    }
}
