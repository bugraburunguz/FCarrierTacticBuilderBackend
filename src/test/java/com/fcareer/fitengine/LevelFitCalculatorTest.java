package com.fcareer.fitengine;

import com.fcareer.fitengine.level.LevelFitCalculator;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5c levelFit: OVR bandı (<100 oyuncuda genişler, bant kaydedilir) ve pürüzsüz eşleme 50 + 50·tanh((fit − medyan) / max(IQR, 8)). */
class LevelFitCalculatorTest {

    private static final int MIN_PLAYERS = 100;
    private static final int HALF_WIDTH = 3;
    private static final int MAX_HALF_WIDTH = 15;
    private static final double MAX_JUMP_PER_THREE_POINTS = 25.0;

    private static double[] uniform(double from, double to) {
        return LevelFitCalculator.quantiles(IntStream.rangeClosed(0, 200).mapToDouble((i) -> from + (to - from) * i / 200.0).toArray());
    }

    @Test
    void aDenseBandKeepsThePlusMinusThreeWidth() {
        int[] ovrs = IntStream.range(0, 600).map((i) -> 60 + i % 15).sorted().toArray();
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 66, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.lo()).isEqualTo(63);
        assertThat(band.hi()).isEqualTo(69);
        assertThat(band.count()).isGreaterThanOrEqualTo(MIN_PLAYERS);
    }

    @Test
    void aSparseBandWidensUntilOneHundredPlayersAndRecordsTheWidth() {
        int[] ovrs = IntStream.concat(IntStream.of(88, 88, 89, 90), IntStream.range(0, 300).map((i) -> 70 + i % 20)).sorted().toArray();
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 90, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.count()).isGreaterThanOrEqualTo(MIN_PLAYERS);
        assertThat(band.lo()).isLessThan(87);
        assertThat(band.hi() - band.lo()).isGreaterThan(2 * HALF_WIDTH);
    }

    @Test
    void theBandNeverWidensBeyondTheMaximumHalfWidth() {
        int[] ovrs = {50, 51, 52};
        LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, 51, MIN_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
        assertThat(band.lo()).isEqualTo(51 - MAX_HALF_WIDTH);
        assertThat(band.hi()).isEqualTo(51 + MAX_HALF_WIDTH);
        assertThat(band.count()).isEqualTo(3);
    }

    @Test
    void quantilesOfAUniformRangeAreTheRangeItself() {
        double[] q = LevelFitCalculator.quantiles(IntStream.rangeClosed(0, 100).asDoubleStream().toArray());
        assertThat(q).hasSize(101);
        assertThat(q[0]).isEqualTo(0.0);
        assertThat(q[50]).isEqualTo(50.0);
        assertThat(q[100]).isEqualTo(100.0);
    }

    @Test
    void theBandMedianMapsToFiftyAndTheMappingIsMonotoneAndOpenBounded() {
        double[] q = uniform(60, 90);
        assertThat(LevelFitCalculator.smooth(q, q[50])).isEqualTo(50.0);
        double previous = -1;
        for (double fit = 0; fit <= 100; fit += 0.5) {
            double level = LevelFitCalculator.smooth(q, fit);
            assertThat(level).isGreaterThanOrEqualTo(previous).isBetween(0.0, 100.0);
            previous = level;
        }
        assertThat(LevelFitCalculator.smooth(q, 100)).isLessThan(100.0);
    }

    @Test
    void aThreePointCurrentFitGapMovesLevelFitByAtMostTwentyFivePointsEvenInANarrowBand() {
        double[] narrow = LevelFitCalculator.quantiles(IntStream.range(0, 120).mapToDouble((i) -> 84 + i * 0.01).toArray());
        for (double fit = 70; fit <= 100; fit += 0.5) {
            double jump = Math.abs(LevelFitCalculator.smooth(narrow, fit + 3) - LevelFitCalculator.smooth(narrow, fit));
            assertThat(jump).as("fit=" + fit).isLessThanOrEqualTo(MAX_JUMP_PER_THREE_POINTS);
        }
    }

    @Test
    void aWideBandIsNotFlattenedByTheSpreadFloor() {
        double[] wide = uniform(20, 100);
        double[] narrow = uniform(60, 64);
        assertThat(LevelFitCalculator.smooth(wide, wide[50] + 20)).isLessThan(LevelFitCalculator.smooth(narrow, narrow[50] + 20));
        assertThat(LevelFitCalculator.smooth(wide, wide[50] + 20)).isGreaterThan(50.0);
    }
}
