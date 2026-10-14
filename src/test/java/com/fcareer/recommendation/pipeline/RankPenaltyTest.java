package com.fcareer.recommendation.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-6b: ilk 11 ortalamasının çok altındaki oyuncu (dev kulübe yedek kalitesi) sıralamada cezalanır; unknown ya da yakın fark ceza almaz. */
class RankPenaltyTest {

    private final RecommendProperties.RankPenalty penalty = new RecommendProperties.RankPenalty();

    @Test
    void playersFarBelowTheFirstElevenAreRankedDown() {
        assertThat(penalty.belowSquad(null)).isZero();
        assertThat(penalty.belowSquad(-5.0)).isZero();
        assertThat(penalty.belowSquad(-12.0)).isGreaterThan(0);
        assertThat(penalty.belowSquad(-18.0)).isGreaterThan(penalty.belowSquad(-12.0));
        assertThat(penalty.belowSquad(4.0)).isZero();
    }
}
