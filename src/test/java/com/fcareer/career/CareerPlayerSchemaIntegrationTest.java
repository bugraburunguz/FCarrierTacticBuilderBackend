package com.fcareer.career;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-1 şema: career_player (13 §3.1), user_tactic, career.engine_version/data_version. Veri silen/değiştiren göç yok: eski overlay tabloları yerinde. */
@IntegrationTest
class CareerPlayerSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> columns(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
    }

    @Test
    void careerPlayerCarriesTheDesignedColumns() {
        assertThat(columns("career_player")).contains("career_id", "player_id", "as_of", "team_id", "loan_from_team_id", "loan_until", "contract_until", "wage_eur",
                "release_clause_eur", "value_eur", "value_source", "squad_role", "form", "injury_until", "available", "dob", "height_cm", "weight_kg", "preferred_foot",
                "weak_foot", "skill_moves", "accelerate", "accelerate_source", "ovr", "pot", "positions", "attrs", "playstyles", "role_familiarity", "hidden_traits", "free_agent");
    }

    @Test
    void careerGainsEngineAndDataVersion() {
        assertThat(columns("career")).contains("engine_version", "data_version");
    }

    @Test
    void userTacticStoresFiveSlotsPerUser() {
        assertThat(columns("user_tactic")).contains("id", "user_id", "career_id", "slot", "name", "formation", "build_up", "defensive_approach", "line_height", "slots", "ea_code", "source_replica_id");
    }

    @Test
    void theOldOverlayTablesAreUntouched() {
        assertThat(columns("career_player_rating")).isNotEmpty();
        assertThat(columns("career_player_data")).contains("career_id", "base_player_id", "overall", "potential", "team_id", "free_agent", "loan_from_team_id");
    }

    @Test
    void availableIsDerivedFromTheLoanState() {
        Long userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES ('arch-schema@test.local', 'x') RETURNING id", Long.class);
        Long careerId = jdbc.queryForObject("INSERT INTO career (user_id, game_version_id, name) VALUES (?, 27, 'schema') RETURNING id", Long.class, userId);
        Long playerId = jdbc.queryForObject("INSERT INTO base_player (full_name, gender) VALUES ('Schema Probe', 0) RETURNING id", Long.class);
        jdbc.update("INSERT INTO career_player (career_id, player_id, as_of, ovr, pot, positions, attrs, loan_from_team_id) VALUES (?, ?, DATE '2026-07-01', 60, 70, ARRAY['ST'], ARRAY[1], NULL)", careerId, playerId);
        assertThat(jdbc.queryForObject("SELECT available FROM career_player WHERE career_id = ? AND player_id = ?", Boolean.class, careerId, playerId)).isTrue();
        jdbc.update("UPDATE career_player SET loan_from_team_id = 99 WHERE career_id = ? AND player_id = ?", careerId, playerId);
        assertThat(jdbc.queryForObject("SELECT available FROM career_player WHERE career_id = ? AND player_id = ?", Boolean.class, careerId, playerId)).isFalse();
    }
}
