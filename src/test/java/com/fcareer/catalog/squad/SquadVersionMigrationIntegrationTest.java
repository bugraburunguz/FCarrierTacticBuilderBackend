package com.fcareer.catalog.squad;

import com.fcareer.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FCA-27 AC-1: V52 kadro sürümü şeması, Sürüm 1 doldurma, mevcut kariyerlerin bağlanması ve arşiv deposu. */
@IntegrationTest
class SquadVersionMigrationIntegrationTest {

    private static final String SCRATCH_SCHEMA = "fca27_squad_migration";
    private static final short TEST_GAME = 99;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SquadVersionRepository squadVersions;

    @Test
    void migrationCreatesVersionOneAndBindsExistingCareers() {
        try {
            migrateScratch("51");
            jdbc.update("INSERT INTO " + SCRATCH_SCHEMA + ".game_version (id, code) VALUES (28, 'FC28')");
            long catalogPlayer = jdbc.queryForObject("INSERT INTO " + SCRATCH_SCHEMA + ".base_player (full_name) VALUES ('Mig A') RETURNING id", Long.class);
            long secondPlayer = jdbc.queryForObject("INSERT INTO " + SCRATCH_SCHEMA + ".base_player (full_name) VALUES ('Mig B') RETURNING id", Long.class);
            jdbc.update("INSERT INTO " + SCRATCH_SCHEMA + ".player_rating (base_player_id, game_version_id, overall, positions) VALUES (?, 27, 70, ARRAY['ST']), (?, 27, 65, ARRAY['CB'])",
                    catalogPlayer, secondPlayer);
            long career = jdbc.queryForObject("INSERT INTO " + SCRATCH_SCHEMA + ".career (game_version_id, name) VALUES (27, 'eski') RETURNING id", Long.class);
            long otherGameCareer = jdbc.queryForObject("INSERT INTO " + SCRATCH_SCHEMA + ".career (game_version_id, name) VALUES (28, 'baska') RETURNING id", Long.class);

            migrateScratch("52");

            List<Object[]> versions = jdbc.query("SELECT game_version_id, version_no, label, game_build, exported_at, player_count FROM " + SCRATCH_SCHEMA
                            + ".squad_version ORDER BY game_version_id",
                    (rs, i) -> new Object[] {rs.getShort(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getObject(5), rs.getInt(6)});
            assertThat(versions).hasSize(2);
            assertThat(versions.get(0)).containsExactly((short) 27, 1, "İlk kadro (Sürüm 1)", null, null, 2);
            assertThat(versions.get(1)).containsExactly((short) 28, 1, "İlk kadro (Sürüm 1)", null, null, 0);
            assertThat(boundVersion(career)).isEqualTo(versionOneOf(27));
            assertThat(boundVersion(otherGameCareer)).isEqualTo(versionOneOf(28));
            assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = 'career' AND column_name = 'squad_version_id'",
                    String.class, SCRATCH_SCHEMA)).isEqualTo("NO");
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + SCRATCH_SCHEMA + " CASCADE");
        }
    }

    @Test
    void migrationBackfillsTheValuationArchiveOfAlreadyArchivedVersionsOnly() {
        try {
            migrateScratch("52");
            String s = SCRATCH_SCHEMA + ".";
            long catalog = jdbc.queryForObject("INSERT INTO " + s + "base_player (full_name) VALUES ('Deger A') RETURNING id", Long.class);
            long generated = jdbc.queryForObject("INSERT INTO " + s + "base_player (full_name, owner_career_id) VALUES ('Deger Uretilen', NULL) RETURNING id", Long.class);
            long career = jdbc.queryForObject("INSERT INTO " + s + "career (game_version_id, name, squad_version_id) VALUES (27, 'sahip', "
                    + "(SELECT id FROM " + s + "squad_version WHERE game_version_id = 27)) RETURNING id", Long.class);
            jdbc.update("UPDATE " + s + "base_player SET owner_career_id = ? WHERE id = ?", career, generated);
            jdbc.update("INSERT INTO " + s + "player_valuation (base_player_id, game_version_id, source, potential, value_eur, model_version) VALUES "
                    + "(?, 27, 'MODELED', 88, 5000000, 'm1'), (?, 27, 'MODELED', 70, 100000, 'm1')", catalog, generated);
            long archived = jdbc.queryForObject("SELECT id FROM " + s + "squad_version WHERE game_version_id = 27", Long.class);
            jdbc.update("INSERT INTO " + s + "player_rating_archive (squad_version_id, base_player_id, game_version_id, overall, positions) VALUES (?, ?, 27, 80, ARRAY['ST'])",
                    archived, catalog);
            long latest = jdbc.queryForObject("INSERT INTO " + s + "squad_version (game_version_id, version_no, label) VALUES (27, 2, 'yeni') RETURNING id", Long.class);

            migrateScratch("53");

            assertThat(jdbc.queryForList("SELECT potential FROM " + s + "player_valuation_archive WHERE squad_version_id = ? AND base_player_id = ?", Integer.class,
                    archived, catalog)).as("arşivli sürüm canlı değerlemeyle dolar").containsExactly(88);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + s + "player_valuation_archive WHERE base_player_id = ?", Integer.class, generated))
                    .as("kariyer oyuncusu arşive girmez").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + s + "player_valuation_archive WHERE squad_version_id = ?", Integer.class, latest))
                    .as("arşivi olmayan (en yeni) sürüm doldurulmaz").isZero();
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + SCRATCH_SCHEMA + " CASCADE");
        }
    }

    @Test
    @Transactional
    void archiveCurrentAlsoArchivesOnlyPublicModeledValuationsOfCatalogPlayers() {
        jdbc.update("INSERT INTO game_version (id, code) VALUES (?, 'TEST99')", TEST_GAME);
        long ownerCareer = jdbc.queryForObject("INSERT INTO career (game_version_id, name, squad_version_id) VALUES (27, 'sahip', "
                + "(SELECT max(id) FROM squad_version WHERE game_version_id = 27)) RETURNING id", Long.class);
        long user = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class, "fca31-" + System.nanoTime() + "@test.local");
        long catalog = basePlayer("Deger Katalog", null);
        long generated = basePlayer("Deger Uretilen", ownerCareer);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, overall, positions) VALUES (?, ?, 75, ARRAY['ST']), (?, ?, 60, ARRAY['CM'])",
                catalog, TEST_GAME, generated, TEST_GAME);
        jdbc.update("INSERT INTO player_valuation (base_player_id, game_version_id, source, owner_user_id, potential, value_eur, wage_eur, model_version) VALUES "
                + "(?, ?, 'MODELED', NULL, 86, 4000000, 20000, 'm1'), (?, ?, 'IMPORT', ?, 91, 9000000, NULL, NULL), (?, ?, 'MODELED', NULL, 72, 50000, NULL, 'm1')",
                catalog, TEST_GAME, catalog, TEST_GAME, user, generated, TEST_GAME);
        SquadVersion version = squadVersions.create(TEST_GAME, "Birinci", null, null, 1);

        SquadArchiveResult result = squadVersions.archiveCurrent(version.id(), TEST_GAME);

        assertThat(result.valuations()).isEqualTo(1);
        List<Object[]> rows = jdbc.query("SELECT base_player_id, potential, value_eur, wage_eur, model_version FROM player_valuation_archive WHERE squad_version_id = ?",
                (rs, i) -> new Object[] {rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getString(5)}, version.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly(catalog, 86, 4_000_000L, 20_000L, "m1");
    }

    @Test
    void liveSchemaHasVersionOneForTheDefaultGame() {
        assertThat(squadVersions.findLatest((short) 27)).isPresent();
        assertThat(jdbc.queryForObject("SELECT label FROM squad_version WHERE game_version_id = 27 AND version_no = 1", String.class)).isEqualTo("İlk kadro (Sürüm 1)");
    }

    @Test
    void archiveTablesMirrorTheLiveTables() {
        assertThat(columns("player_rating_archive")).containsExactlyInAnyOrderElementsOf(withSquadVersion(columns("player_rating")));
        assertThat(columns("club_profile_archive")).containsExactlyInAnyOrderElementsOf(withSquadVersion(columns("club_profile")));
        assertThat(columns("dataset_version")).contains("squad_version_id");
    }

    @Test
    void archiveIsIndexedByVersionAndClubWithoutARedundantVersionIndex() {
        List<String> indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'player_rating_archive'", String.class);

        assertThat(indexes).anyMatch((d) -> d.contains("(squad_version_id, club_id)"));
        assertThat(indexes).noneMatch((d) -> d.endsWith("(squad_version_id)"));
    }

    @Test
    @Transactional
    void createListsNewestFirstAndTracksLatest() {
        jdbc.update("INSERT INTO game_version (id, code) VALUES (?, 'TEST99')", TEST_GAME);
        assertThat(squadVersions.findLatest(TEST_GAME)).isEmpty();

        SquadVersion first = squadVersions.create(TEST_GAME, "Birinci", null, null, 10);
        SquadVersion second = squadVersions.create(TEST_GAME, "Kadro güncellemesi 22.11.2026 (1.0.142.20665)", "1.0.142.20665",
                Instant.parse("2026-11-22T10:00:00Z"), 12);

        assertThat(first.versionNo()).isEqualTo(1);
        assertThat(second.versionNo()).isEqualTo(2);
        assertThat(second.gameBuild()).isEqualTo("1.0.142.20665");
        assertThat(second.exportedAt()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
        assertThat(squadVersions.findAllByGameVersion(TEST_GAME)).extracting(SquadVersion::id).containsExactly(second.id(), first.id());
        assertThat(squadVersions.findLatest(TEST_GAME)).map(SquadVersion::id).contains(second.id());
        assertThat(squadVersions.findById(first.id())).map(SquadVersion::label).contains("Birinci");
        assertThat(squadVersions.isLatest(second.id())).isTrue();
        assertThat(squadVersions.isLatest(first.id())).isFalse();
    }

    @Test
    @Transactional
    void archiveCurrentCopiesOnlyCatalogRowsOfTheGame() {
        jdbc.update("INSERT INTO game_version (id, code) VALUES (?, 'TEST99')", TEST_GAME);
        long ownerCareer = jdbc.queryForObject("INSERT INTO career (game_version_id, name, squad_version_id) VALUES (27, 'sahip', "
                + "(SELECT max(id) FROM squad_version WHERE game_version_id = 27)) RETURNING id", Long.class);
        long catalogA = basePlayer("Arsiv A", null);
        long catalogB = basePlayer("Arsiv B", null);
        long generated = basePlayer("Arsiv Uretilen", ownerCareer);
        int clubId = jdbc.queryForObject("INSERT INTO club (name) VALUES ('Arsiv FC') RETURNING id", Integer.class);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, club_id, overall, positions, finishing, face_stats) VALUES "
                + "(?, ?, ?, 81, ARRAY['ST'], 85, ARRAY[1,2,3,4,5,6]::smallint[]), (?, ?, ?, 70, ARRAY['CB'], 30, NULL), (?, ?, ?, 60, ARRAY['GK'], 10, NULL)",
                catalogA, TEST_GAME, clubId, catalogB, TEST_GAME, clubId, generated, TEST_GAME, clubId);
        jdbc.update("INSERT INTO club_profile (club_id, game_version_id, domestic_prestige, club_worth_eur) VALUES (?, ?, 7, 1000000)", clubId, TEST_GAME);
        SquadVersion version = squadVersions.create(TEST_GAME, "Birinci", null, null, 2);

        SquadArchiveResult result = squadVersions.archiveCurrent(version.id(), TEST_GAME);

        assertThat(result.players()).isEqualTo(2);
        assertThat(result.clubs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_rating_archive WHERE squad_version_id = ?", Integer.class, version.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT finishing FROM player_rating_archive WHERE squad_version_id = ? AND base_player_id = ?", Integer.class, version.id(), catalogA))
                .isEqualTo(85);
        assertThat(jdbc.queryForObject("SELECT face_stats[6] FROM player_rating_archive WHERE squad_version_id = ? AND base_player_id = ?", Integer.class, version.id(), catalogA))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT domestic_prestige FROM club_profile_archive WHERE squad_version_id = ? AND club_id = ?", Integer.class, version.id(), clubId))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_rating WHERE game_version_id = ?", Integer.class, TEST_GAME)).isEqualTo(3);
    }

    private long basePlayer(String name, Long ownerCareerId) {
        return jdbc.queryForObject("INSERT INTO base_player (full_name, owner_career_id) VALUES (?, ?) RETURNING id", Long.class, name, ownerCareerId);
    }

    private Long boundVersion(long careerId) {
        return jdbc.queryForObject("SELECT squad_version_id FROM " + SCRATCH_SCHEMA + ".career WHERE id = ?", Long.class, careerId);
    }

    private Long versionOneOf(int gameVersionId) {
        return jdbc.queryForObject("SELECT id FROM " + SCRATCH_SCHEMA + ".squad_version WHERE game_version_id = ? AND version_no = 1", Long.class, gameVersionId);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", String.class, table);
    }

    private List<String> withSquadVersion(List<String> liveColumns) {
        List<String> expected = new java.util.ArrayList<>(liveColumns);
        expected.add("squad_version_id");
        return expected;
    }

    private void migrateScratch(String target) {
        Flyway.configure().dataSource(dataSource).schemas(SCRATCH_SCHEMA).locations("classpath:db/migration").target(target).load().migrate();
    }
}
