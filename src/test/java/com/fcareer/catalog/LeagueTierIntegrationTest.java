package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.service.LeagueTierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-BUG-17: EA lig id'sine göre tier yazılır; ML'e giden league_tier artık hep null değildir. */
@IntegrationTest
class LeagueTierIntegrationTest {

    @Autowired
    private LeagueTierService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tiersAreWrittenByEaLeagueIdAndUnknownLeaguesAreLeftAlone() {
        jdbc.update("DELETE FROM league WHERE name IN ('Tier Test Top', 'Tier Test Unknown')");
        jdbc.update("UPDATE league SET ea_league_id = NULL WHERE ea_league_id IN (13, 61)");
        jdbc.update("INSERT INTO league (name, ea_league_id) VALUES ('Tier Test Top', 13)");
        jdbc.update("INSERT INTO league (name, ea_league_id) VALUES ('Tier Test Low', 61)");
        jdbc.update("INSERT INTO league (name, ea_league_id) VALUES ('Tier Test Unknown', 999999)");

        assertThat(service.apply()).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT tier FROM league WHERE name = 'Tier Test Top'", Short.class)).isEqualTo((short) 1);
        assertThat(jdbc.queryForObject("SELECT tier FROM league WHERE name = 'Tier Test Low'", Short.class)).isEqualTo((short) 4);
        assertThat(jdbc.queryForObject("SELECT tier FROM league WHERE name = 'Tier Test Unknown'", Short.class)).isNull();
        assertThat(service.apply()).as("ikinci uygulama değişiklik yapmaz").isZero();
    }
}
