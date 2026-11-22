package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RatingIndexMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void playerRatingKeepsASingleVersionOverallIndex() {
        List<String> names = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE tablename = 'player_rating' AND indexdef LIKE '%(game_version_id, overall)'""", String.class);

        assertThat(names).containsExactly("ix_rating_pot_filter");
    }
}
