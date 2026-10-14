package com.fcareer.tacticanalysis;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-7b: etiket tabanlı eski kurallar kalktı; statik seed tablosu `tactic_rule` Flyway ile düştü (yedek: backups/tactic_rule_before_drop_*.sql). */
@IntegrationTest
class TacticRuleTableDroppedIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theOldTagBasedRuleTableIsGone() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'tactic_rule'", Integer.class);
        assertThat(count).isZero();
    }
}
