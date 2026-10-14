package com.fcareer.versioning;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.versioning.service.VersioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Liste özeti: ps_diff / attr_diff NULL olan bir fark satırı aramayı düşürmemeli (tam pakette başka testin bıraktığı sürüm verisi 500 üretiyordu). */
@IntegrationTest
class VersioningSummariesIntegrationTest {

    private static final short GAME_VERSION = 27;

    @Autowired
    private VersioningService versioning;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void aDiffRowWithoutStyleChangesStillSummarises() {
        Long playerId = jdbc.queryForObject("SELECT id FROM base_player ORDER BY id LIMIT 1", Long.class);
        int next = jdbc.queryForObject("SELECT COALESCE(max(version_no), 0) + 1 FROM dataset_version WHERE scope = 'CATALOG' AND career_id IS NULL AND game_version_id = ?", Integer.class, GAME_VERSION);
        long versionId = jdbc.queryForObject("INSERT INTO dataset_version (scope, game_version_id, version_no, source) VALUES ('CATALOG', ?, ?, 'TEST') RETURNING id", Long.class, GAME_VERSION, next);
        jdbc.update("INSERT INTO player_version_diff (version_id, base_player_id, change_type, ovr_old, ovr_new, attr_diff, ps_diff) VALUES (?, ?, 'UPGRADE', 70, 72, NULL, NULL)", versionId, playerId);
        try {
            assertThat(versioning.summaries(List.of(playerId), GAME_VERSION)).containsKey(playerId);
        } finally {
            jdbc.update("DELETE FROM player_version_diff WHERE version_id = ?", versionId);
            jdbc.update("DELETE FROM dataset_version WHERE id = ?", versionId);
        }
    }
}
