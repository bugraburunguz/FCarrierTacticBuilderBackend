package com.fcareer.recommendation;

import com.fcareer.recommendation.service.ScoutingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ENG-02: bütçe bilinmiyorsa oran bilinmiyor (null); her değerli aday "gerçekçi değil" sayılıp gizlenmez. */
class ScoutingValueRatioTest {

    @Test
    void anUnknownOrZeroBudgetHasNoRatio() {
        assertThat(ScoutingService.valueRatio(5_000_000L, null)).isNull();
        assertThat(ScoutingService.valueRatio(5_000_000L, 0L)).isNull();
    }

    @Test
    void aKnownBudgetGivesTheRatio() {
        assertThat(ScoutingService.valueRatio(5_000_000L, 10_000_000L)).isEqualTo(0.5);
        assertThat(ScoutingService.valueRatio(null, 10_000_000L)).isNull();
    }
}
