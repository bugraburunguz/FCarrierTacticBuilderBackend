package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.ingestion.base.CareerPlayerWriter;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class CareerSquadVersionPlayerIntegrationTest {

    private static final short GAME = 27;
    private static final int ARCHIVED_POT = 80;
    private static final long ARCHIVED_VALUE = 3_000_000L;
    private static final int LIVE_POT = 95;
    private static final long LIVE_VALUE = 90_000_000L;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private CareerPlayerWriter writer;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private long player;
    private long newcomer;
    private long oldCareer;
    private long newCareer;

    @BeforeEach
    void seedTwoVersions() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
            entityManager.flush();
            entityManager.clear();
        }
        player = jdbc.queryForObject("""
                SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.retired_at IS NULL AND r.finishing IS NOT NULL AND r.finishing <> 11
                 ORDER BY r.base_player_id LIMIT 1""", Long.class);
        modeled(player, ARCHIVED_POT, ARCHIVED_VALUE);
        SquadVersion old = squadVersions.findLatest(GAME).orElseThrow();
        oldCareer = career(old.id());
        squadVersions.archiveCurrent(old.id(), GAME);
        SquadVersion fresh = squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
        newCareer = career(fresh.id());
        jdbc.update("UPDATE player_rating SET finishing = 11 WHERE base_player_id = ? AND game_version_id = 27", player);
        modeled(player, LIVE_POT, LIVE_VALUE);
        newcomer = jdbc.queryForObject("INSERT INTO base_player (full_name) VALUES ('Yeni Sürüm Oyuncusu') RETURNING id", Long.class);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, overall, positions, finishing) VALUES (?, 27, 75, ARRAY['ST'], 70)", newcomer);
        writer.syncCareer(oldCareer);
    }

    @Test
    void catalogFallbackOfAnOldVersionCareerReadsItsArchive() {
        Integer recorded = playerService.fitPlayers(List.of(player), GAME, oldCareer).get(0).getAttrs().get(Attr.FINISHING);
        Integer live = playerService.fitPlayers(List.of(player), GAME, null).get(0).getAttrs().get(Attr.FINISHING);
        assertThat(jdbc.update("UPDATE career_player SET attrs = '{}' WHERE career_id = ? AND player_id = ?", oldCareer, player)).isEqualTo(1);

        FitPlayer fallback = playerService.fitPlayers(List.of(player), GAME, oldCareer).get(0);

        assertThat(recorded).as("kayıt arşivden kuruldu").isNotEqualTo(live);
        assertThat(fallback.getAttrs().get(Attr.FINISHING)).as("kayıtta öznitelik yok: kariyerin sürümündeki arşiv").isEqualTo(recorded);
        assertThatThrownBy(() -> playerService.fitPlayers(List.of(newcomer), GAME, oldCareer))
                .as("yeni sürümde eklenen oyuncu eski sürüm kariyerinde yok").isInstanceOf(NotFoundException.class);
        assertThat(playerService.fitPlayers(List.of(newcomer), GAME, newCareer).get(0).getAttrs().get(Attr.FINISHING))
                .as("en yeni sürüm kariyeri canlı katalog").isEqualTo(70);
    }

    @Test
    void valuationOfAnOldVersionCareerComesFromItsArchive() {
        jdbc.update("UPDATE career_player SET pot = NULL WHERE career_id = ? AND player_id = ?", oldCareer, player);

        FitPlayer old = playerService.fitPlayers(List.of(player), GAME, oldCareer).get(0);
        FitPlayer latest = playerService.fitPlayers(List.of(player), GAME, newCareer).get(0);

        assertThat(old.getPotential()).as("kayıtta POT yok: arşiv tahmini").isEqualTo(ARCHIVED_POT);
        assertThat(old.getValueEur()).isEqualTo(ARCHIVED_VALUE);
        assertThat(latest.getValueEur()).isEqualTo(LIVE_VALUE);
    }

    @Test
    void searchMarksTheEstimateFromTheArchivedValuation() {
        jdbc.update("UPDATE career_player SET live = false, value_eur = NULL WHERE career_id = ? AND player_id = ?", oldCareer, player);
        jdbc.update("DELETE FROM player_valuation WHERE base_player_id = ? AND game_version_id = 27 AND source = 'MODELED' AND owner_user_id IS NULL", player);

        assertThat(playerService.summary(player, GAME, oldCareer).isValuationEstimated()).as("arşivde değerleme var").isTrue();
        assertThat(playerService.summary(player, GAME, oldCareer).getValueEur()).isEqualTo(ARCHIVED_VALUE);
    }

    @Test
    void latestVersionChangesAreHiddenFromAnOldVersionCareer() {
        writer.syncCareer(newCareer);
        long version = jdbc.queryForObject("""
                INSERT INTO dataset_version (scope, game_version_id, version_no, source, label)
                VALUES ('CATALOG', 27, (SELECT COALESCE(max(version_no), 0) + 1 FROM dataset_version WHERE scope = 'CATALOG' AND career_id IS NULL AND game_version_id = 27),
                        'TEST', 'fca31') RETURNING id""", Long.class);
        jdbc.update("INSERT INTO player_version_diff (version_id, base_player_id, change_type, ovr_old, ovr_new) VALUES (?, ?, 'UPGRADE', 70, 74)", version, player);

        PlayerDetailResponse old = playerService.detail(player, GAME, oldCareer);
        PlayerDetailResponse latest = playerService.detail(player, GAME, newCareer);
        PlayerDetailResponse catalog = playerService.detail(player, GAME, null);

        assertThat(old.getDiff()).as("eski sürüm kariyeri yeni sürümün farkını görmez").isNull();
        assertThat(old.getSummary().getChanged()).isNotEqualTo(Boolean.TRUE);
        assertThat(old.getSummary().getChangeSummary()).isNull();
        assertThat(latest.getDiff()).isNotNull();
        assertThat(latest.getSummary().getChanged()).isTrue();
        assertThat(catalog.getDiff()).isNotNull();
    }

    private void modeled(long playerId, int potential, long valueEur) {
        jdbc.update("""
                INSERT INTO player_valuation (base_player_id, game_version_id, source, potential, value_eur, model_version) VALUES (?, 27, 'MODELED', ?, ?, 'fca31-test')
                ON CONFLICT ON CONSTRAINT uq_valuation DO UPDATE SET potential = EXCLUDED.potential, value_eur = EXCLUDED.value_eur""", playerId, potential, valueEur);
    }

    private long career(long squadVersionId) {
        Long userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class,
                "fca31-" + System.nanoTime() + "@test.local");
        return jdbc.queryForObject("INSERT INTO career (user_id, game_version_id, name, squad_version_id) VALUES (?, 27, 'surum', ?) RETURNING id",
                Long.class, userId, squadVersionId);
    }
}
