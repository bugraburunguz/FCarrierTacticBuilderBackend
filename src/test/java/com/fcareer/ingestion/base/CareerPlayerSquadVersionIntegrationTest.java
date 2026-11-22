package com.fcareer.ingestion.base;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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

    private int rows(long careerId) {
        return jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, careerId);
    }
}
