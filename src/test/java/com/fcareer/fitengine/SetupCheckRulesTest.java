package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.service.SetupCheckRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FIT-2 madde 7: kurulum kontrolleri deklaratif (seed/setup-checks.fc27.json); eşikler verified:false, her kontrolün önerisi seed'de. */
class SetupCheckRulesTest {

    private final SetupCheckRules rules = new SetupCheckRules(new ObjectMapper());

    @Test
    void seedCarriesAllChecksWithAdviceAndUnverifiedThresholds() {
        assertThat(rules.verified()).isFalse();
        assertThat(rules.checks()).extracting(SetupCheckRules.Check::id)
                .containsExactly("high_line_pace", "low_block_air", "mid_block_reading", "short_build_up_passing", "short_build_up_keeper", "counter_runners");
        assertThat(rules.checks()).allSatisfy(c -> {
            assertThat(c.advice()).isNotBlank();
            assertThat(c.detail()).contains("{threshold}");
        });
    }

    @Test
    void depthAndBuildUpSelectChecks() {
        assertThat(rules.applicable("Short", 85)).extracting(SetupCheckRules.Check::id).containsExactly("high_line_pace", "short_build_up_passing", "short_build_up_keeper");
        assertThat(rules.applicable("Counter", 35)).extracting(SetupCheckRules.Check::id).containsExactly("low_block_air", "counter_runners");
        assertThat(rules.applicable("Balanced", 36)).extracting(SetupCheckRules.Check::id).containsExactly("mid_block_reading");
        assertThat(rules.applicable("Balanced", 64)).extracting(SetupCheckRules.Check::id).containsExactly("mid_block_reading");
        SetupCheckRules.Check pace = rules.checks().get(0);
        assertThat(pace.thresholdAt(65)).isEqualTo(66);
        assertThat(pace.thresholdAt(85)).as("agresif hat").isEqualTo(72);
    }
}
