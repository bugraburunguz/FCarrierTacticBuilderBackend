package com.fcareer.ingestion.base;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.catalog.squad.SquadVersionService;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FCA-27 AC-5: career_player yeniden kurulurken kariyer kendi kadro sürümünün baz verisini okur. Eski sürüme bağlı kariyer arşivden (eski OVR),
 * en yeni sürüme bağlı kariyer canlı tablodan (yeni OVR); kariyerin kendi ürettiği oyuncular her zaman canlı tablodan gelir.
 */
@IntegrationTest
@Transactional
class CareerPlayerSquadVersionIntegrationTest {

    private static final short GAME = 27;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CareerPlayerWriter writer;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private SquadVersionService squadVersionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void careerReadsTheBaseRatingsOfItsOwnSquadVersion() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL AND r.overall < 90 ORDER BY r.base_player_id LIMIT 1""", Long.class);
        int oldOvr = jdbc.queryForObject("SELECT overall FROM player_rating WHERE base_player_id = ? AND game_version_id = 27", Integer.class, player);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        long oldCareer = career(old.id());

        // Yeni BASE yüklemesi: mevcut veri eski sürümle arşive donar, yeni sürüm açılır, canlı tablo yeni değerleri alır.
        squadVersions.archiveCurrent(old.id(), GAME);
        SquadVersion fresh = squadVersions.create(GAME, "Kadro güncellemesi", "1.0.142.20665", null, 0);
        jdbc.update("UPDATE player_rating SET overall = ? WHERE base_player_id = ? AND game_version_id = 27", oldOvr + 7, player);
        long newcomer = jdbc.queryForObject("INSERT INTO base_player (full_name) VALUES ('Yeni Sürüm Oyuncusu') RETURNING id", Long.class);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, overall, positions) VALUES (?, 27, 75, ARRAY['ST'])", newcomer);
        long newCareer = career(fresh.id());
        long generated = jdbc.queryForObject("INSERT INTO base_player (full_name, owner_career_id) VALUES ('Kariyer Genci', ?) RETURNING id", Long.class, oldCareer);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, overall, positions) VALUES (?, 27, 55, ARRAY['CM'])", generated);

        writer.syncCareer(oldCareer);
        writer.syncCareer(newCareer);

        assertThat(ovr(oldCareer, player)).as("Sürüm 1 kariyeri eski OVR").isEqualTo(oldOvr);
        assertThat(ovr(newCareer, player)).as("en yeni sürüm kariyeri yeni OVR").isEqualTo(oldOvr + 7);
        assertThat(ovr(oldCareer, newcomer)).as("yeni sürümde eklenen oyuncu eski kariyerde yok").isNull();
        assertThat(ovr(newCareer, newcomer)).isEqualTo(75);
        assertThat(ovr(oldCareer, generated)).as("kariyerin kendi oyuncusu canlı tablodan").isEqualTo(55);
        assertThat(rows(oldCareer)).as("eski kariyer arşivdeki bütün katalog oyuncularını taşır")
                .isEqualTo(jdbc.queryForObject("""
                        SELECT count(*) + 1 FROM player_rating_archive a JOIN base_player b ON b.id = a.base_player_id
                         WHERE a.squad_version_id = ? AND b.retired_at IS NULL""", Integer.class, old.id()));
    }

    @Test
    void careerReadsThePotentialAndValueOfItsOwnSquadVersion() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL AND r.overall BETWEEN 60 AND 75 ORDER BY r.base_player_id LIMIT 1""", Long.class);
        jdbc.update("UPDATE base_player SET dob = CURRENT_DATE - INTERVAL '19 years' WHERE id = ?", player);
        modeled(player, 80, 3_000_000L);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        long oldCareer = career(old.id());
        squadVersions.archiveCurrent(old.id(), GAME);
        SquadVersion fresh = squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
        long newCareer = career(fresh.id());
        modeled(player, 95, 90_000_000L);
        long generated = jdbc.queryForObject("INSERT INTO base_player (full_name, owner_career_id) VALUES ('Kariyer Genci', ?) RETURNING id", Long.class, oldCareer);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, overall, positions) VALUES (?, 27, 55, ARRAY['CM'])", generated);
        modeled(generated, 71, 200_000L);

        writer.syncCareer(oldCareer);
        writer.syncCareer(newCareer);

        assertThat(pot(oldCareer, player)).as("eski sürüm kariyeri arşivdeki POT").isEqualTo(80);
        assertThat(pot(newCareer, player)).as("en yeni sürüm kariyeri canlı POT").isEqualTo(95);
        assertThat(value(oldCareer, player)).as("değer de eski POT'tan").isLessThan(value(newCareer, player));
        assertThat(pot(oldCareer, generated)).as("kariyerin kendi oyuncusu canlı değerlemeden").isEqualTo(71);
        assertThat(value(oldCareer, generated)).isNotNull();
    }

    @Test
    void careerReadsTheBaseLoanOfItsOwnSquadVersion() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL ORDER BY r.base_player_id LIMIT 1""", Long.class);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = 777, loan_until = DATE '2027-06-30' WHERE base_player_id = ? AND game_version_id = 27", player);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        long oldCareer = career(old.id());
        squadVersions.archiveCurrent(old.id(), GAME);
        SquadVersion fresh = squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = NULL, loan_until = NULL WHERE base_player_id = ? AND game_version_id = 27", player);
        long newCareer = career(fresh.id());

        writer.syncCareer(oldCareer);
        writer.syncCareer(newCareer);

        assertThat(jdbc.queryForMap("SELECT loan_from_team_id, loan_until FROM career_player WHERE career_id = ? AND player_id = ?", oldCareer, player))
                .as("eski sürüm kariyeri arşivdeki kiralığı görür")
                .containsEntry("loan_from_team_id", 777).containsEntry("loan_until", java.sql.Date.valueOf("2027-06-30"));
        assertThat(jdbc.queryForObject("SELECT loan_from_team_id FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, newCareer, player))
                .as("yeni sürümde kiralık bitmiş").isNull();
    }

    @Test
    void importedCareerLoansOverrideTheBaseLoan() {
        List<Long> players = jdbc.queryForList("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL ORDER BY r.base_player_id LIMIT 2""", Long.class);
        long backHome = players.get(0);
        long stillAway = players.get(1);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = 777, loan_until = DATE '2027-06-30' WHERE base_player_id IN (?, ?) AND game_version_id = 27",
                backHome, stillAway);
        long careerId = career(squadVersions.findLatest(GAME).orElseThrow().id());
        jdbc.update("INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id) VALUES (?, ?, 70, 75, 777)", careerId, backHome);
        jdbc.update("INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id, loan_from_team_id) VALUES (?, ?, 70, 75, 5000, 555)",
                careerId, stillAway);

        writer.syncCareer(careerId);

        assertThat(jdbc.queryForObject("SELECT loan_from_team_id FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, backHome))
                .as("kariyer kaydında kiralık bitmiş").isNull();
        assertThat(jdbc.queryForObject("SELECT loan_from_team_id FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, stillAway))
                .as("kariyer kaydının kiralığı").isEqualTo(555);
    }

    @Test
    void aSeededLoanEndsWhenTheImportedCareerShowsThePlayerBack() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL ORDER BY r.base_player_id LIMIT 1""", Long.class);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = 777, loan_until = DATE '2027-06-30' WHERE base_player_id = ? AND game_version_id = 27", player);
        long careerId = career(squadVersions.findLatest(GAME).orElseThrow().id());
        writer.seedNewCareer(careerId);
        jdbc.update("INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id) VALUES (?, ?, 70, 75, 777)", careerId, player);

        writer.syncCareer(careerId);

        assertThat(jdbc.queryForMap("SELECT loan_from_team_id, loan_until FROM career_player WHERE career_id = ? AND player_id = ?", careerId, player))
                .containsEntry("loan_from_team_id", null).containsEntry("loan_until", null);
    }

    @Test
    void theLoanedOutPlayersOfAClubComeFromTheCareersSquadVersion() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL ORDER BY r.base_player_id LIMIT 1""", Long.class);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = 9100001 WHERE base_player_id = ? AND game_version_id = 27", player);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        squadVersions.archiveCurrent(old.id(), GAME);
        SquadVersion fresh = squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
        jdbc.update("UPDATE player_rating SET loan_from_team_id = NULL WHERE base_player_id = ? AND game_version_id = 27", player);

        assertThat(squadVersionService.findCatalogLoanedOut(old.id(), 9100001, GAME)).extracting(PlayerRatingEntity::getBasePlayerId)
                .as("eski sürüm arşivden").containsExactly(player);
        assertThat(squadVersionService.findCatalogLoanedOut(fresh.id(), 9100001, GAME)).as("yeni sürümde kiralık bitti").isEmpty();
    }

    @Test
    void careerOverlayStillWinsOverTheArchivedBase() {
        long player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL ORDER BY r.base_player_id DESC LIMIT 1""", Long.class);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        long oldCareer = career(old.id());
        squadVersions.archiveCurrent(old.id(), GAME);
        squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
        jdbc.update("UPDATE player_rating SET finishing = 11 WHERE base_player_id = ? AND game_version_id = 27", player);
        jdbc.update("INSERT INTO career_player_rating (career_id, base_player_id, game_version_id, overall, positions, finishing) VALUES (?, ?, 27, 66, ARRAY['ST'], 93)",
                oldCareer, player);

        writer.syncCareer(oldCareer);

        int finishingIndex = jdbc.queryForObject("SELECT array_position(ARRAY[" + attrColumnsLiteral() + "]::text[], 'finishing')", Integer.class);
        assertThat(jdbc.queryForObject("SELECT attrs[" + finishingIndex + "] FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, oldCareer, player))
                .as("kariyer overlay'i (career_player_rating) arşiv tabanının üstüne yazılır").isEqualTo(93);
    }

    @Test
    void seedingANewCareerReplacesTheStatisticsOfADeletedOne() throws Exception {
        long latest = squadVersions.findLatest(GAME).orElseThrow().id();
        long deleted = career(latest);
        writer.seedNewCareer(deleted);
        jdbc.execute("ANALYZE career_player");
        jdbc.update("DELETE FROM career_player WHERE career_id = ?", deleted);
        long fresh = career(latest);

        writer.seedNewCareer(fresh);

        int rows = jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, fresh);
        String plan = jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM career_player WHERE career_id = " + fresh, String.class);
        int estimated = new com.fasterxml.jackson.databind.ObjectMapper().readTree(plan).get(0).get("Plan").get("Plan Rows").asInt();
        assertThat(estimated).as("ilk doldurmadan sonra planlayıcı yeni kariyerin satırlarını görür").isGreaterThanOrEqualTo(rows / 2);
    }

    private static String attrColumnsLiteral() {
        StringBuilder out = new StringBuilder();
        for (com.fcareer.fitengine.model.Attr attr : com.fcareer.fitengine.model.Attr.values()) {
            out.append(out.isEmpty() ? "" : ",").append('\'').append(attr.getColumn()).append('\'');
        }
        return out.toString();
    }

    private long career(long squadVersionId) {
        Long userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class,
                "squad-" + System.nanoTime() + "@test.local");
        return jdbc.queryForObject("INSERT INTO career (user_id, game_version_id, name, squad_version_id) VALUES (?, 27, 'surum', ?) RETURNING id",
                Long.class, userId, squadVersionId);
    }

    private Integer ovr(long careerId, long playerId) {
        return jdbc.queryForList("SELECT ovr FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, playerId).stream().findFirst().orElse(null);
    }

    private void modeled(long playerId, int potential, long valueEur) {
        jdbc.update("""
                INSERT INTO player_valuation (base_player_id, game_version_id, source, potential, value_eur, model_version) VALUES (?, 27, 'MODELED', ?, ?, 'fca31-test')
                ON CONFLICT ON CONSTRAINT uq_valuation DO UPDATE SET potential = EXCLUDED.potential, value_eur = EXCLUDED.value_eur""", playerId, potential, valueEur);
    }

    private Integer pot(long careerId, long playerId) {
        return jdbc.queryForList("SELECT pot FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, playerId).stream().findFirst().orElse(null);
    }

    private Long value(long careerId, long playerId) {
        return jdbc.queryForList("SELECT value_eur FROM career_player WHERE career_id = ? AND player_id = ?", Long.class, careerId, playerId).stream().findFirst().orElse(null);
    }

    private int rows(long careerId) {
        return jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, careerId);
    }
}
