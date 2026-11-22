package com.fcareer.manager;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.manager.ManagerModels.Background;
import com.fcareer.manager.ManagerModels.Difficulty;
import com.fcareer.manager.ManagerModels.Licence;
import com.fcareer.manager.ManagerModels.ManagerProfile;
import com.fcareer.manager.ManagerModels.Priority;
import com.fcareer.manager.ManagerModels.RecommendationRequest;
import com.fcareer.manager.ManagerModels.Recommendations;
import com.fcareer.manager.ManagerModels.Style;
import com.fcareer.manager.ManagerRepository.ClubStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FCA-27 AC-6: kulüp önerisi seçilen kadro sürümünün kulüp profilleri ve kadrolarıyla hesaplanır; sürüm verilmezse en yeni (canlı tablolar). */
@IntegrationTest
@Transactional
class ManagerSquadVersionIntegrationTest {

    private static final short GAME = 27;
    private static final long USER = 1L;
    private static final ManagerProfile PROFILE = new ManagerProfile("Buğra", Licence.NATIONAL_A, Background.PRO_REGIONAL, null, Style.COUNTER,
            List.of(Priority.YOUTH, Priority.QUICK_SUCCESS), Difficulty.MEDIUM, null, null);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private ManagerRepository repository;
    @Autowired
    private ManagerService service;
    @Autowired
    private ClubFitCalculator fitCalculator;
    @Autowired
    private com.fcareer.catalog.service.PlayerService playerService;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private SquadVersion old;
    private SquadVersion fresh;

    @BeforeEach
    void seedTwoVersions() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        jdbc.update("DELETE FROM club_profile");
        List<Integer> clubs = jdbc.queryForList("""
                SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id JOIN club c ON c.id = r.club_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND COALESCE(c.gender, 0) = 0
                 GROUP BY r.club_id HAVING count(*) >= 11 ORDER BY r.club_id""", Integer.class);
        for (int i = 0; i < clubs.size(); i++) {
            jdbc.update("""
                    INSERT INTO club_profile (club_id, game_version_id, domestic_prestige, international_prestige, popularity, club_worth_eur, youth_development, league_level)
                    VALUES (?, 27, ?, ?, 5, ?, ?, 1)""", clubs.get(i), 1 + i % 10, 1 + (i * 3) % 10, 1_000_000L * (i + 1), (i * 7) % 11);
        }
        jdbc.update("""
                INSERT INTO player_valuation (base_player_id, game_version_id, source, potential, value_eur, model_version)
                SELECT r.base_player_id, 27, 'MODELED', r.overall + 5, r.overall * 100000, 'fca31-test'
                  FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL
                ON CONFLICT ON CONSTRAINT uq_valuation DO UPDATE SET potential = EXCLUDED.potential, value_eur = EXCLUDED.value_eur""");
        old = squadVersions.findLatest(GAME).orElseThrow();
        squadVersions.archiveCurrent(old.id(), GAME);
        fresh = squadVersions.create(GAME, "Kadro güncellemesi", null, null, 0);
    }

    @Test
    void clubStatsUseTheSquadsOfTheChosenVersion() {
        ClubStat before = repository.clubStats(GAME, 0, new ManagerProperties()).get(0);
        jdbc.update("UPDATE player_rating SET overall = overall + 10 WHERE club_id = ? AND game_version_id = 27", before.clubId());

        assertThat(statOf(repository.clubStats(GAME, 0, new ManagerProperties(), old.id()), before.clubId()).avgOvr())
                .as("eski sürüm arşivdeki kadroyla").isEqualTo(before.avgOvr());
        assertThat(statOf(repository.clubStats(GAME, 0, new ManagerProperties(), null), before.clubId()).avgOvr())
                .as("en yeni sürüm canlı kadroyla").isCloseTo(before.avgOvr() + 10, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void recommendationsReadTheClubProfilesOfTheChosenVersion() {
        jdbc.update("DELETE FROM club_profile");
        RecommendationRequest request = new RecommendationRequest(PROFILE, null, null, null);

        Recommendations latest = service.recommend(USER, request, null);
        Recommendations explicitLatest = service.recommend(USER, request, fresh.id());
        Recommendations archived = service.recommend(USER, request, old.id());
        Recommendations archivedFromBody = service.recommend(USER, new RecommendationRequest(PROFILE, null, null, old.id()));

        assertThat(latest.catalogReady()).as("en yeni sürümde kulüp profili yok").isFalse();
        assertThat(explicitLatest.catalogReady()).isFalse();
        assertThat(archived.catalogReady()).as("eski sürümün kulüp profilleri arşivde").isTrue();
        assertThat(archived.cards()).isNotEmpty();
        assertThat(archivedFromBody.catalogReady()).as("istek gövdesindeki squadVersionId uca kadar taşınır").isTrue();
        assertThatThrownBy(() -> service.recommend(USER, request, -1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clubFitUsesTheArchivedSquadOfTheChosenVersion() {
        ClubStat club = repository.clubStats(GAME, 0, new ManagerProperties()).get(0);
        jdbc.update("UPDATE player_rating SET club_id = NULL WHERE club_id = ? AND game_version_id = 27", club.clubId());

        ClubFitCalculator.ClubFit live = fitCalculator.fit((int) club.clubId(), GAME, "4-4-2", Style.COUNTER, "fca27-live-" + System.nanoTime(), null);
        ClubFitCalculator.ClubFit archived = fitCalculator.fit((int) club.clubId(), GAME, "4-4-2", Style.COUNTER, "fca27-old-" + System.nanoTime(), old.id());

        assertThat(live.squadFit()).as("canlıda kulübün kadrosu yok").isNull();
        assertThat(archived.squadFit()).as("eski sürümün kadrosuyla uyum").isNotNull();
    }

    @Test
    void clubStatsUseTheValuationsOfTheChosenVersion() {
        ClubStat before = repository.clubStats(GAME, 0, new ManagerProperties()).get(0);
        jdbc.update("""
                UPDATE player_valuation SET value_eur = value_eur * 10 WHERE game_version_id = 27 AND source = 'MODELED' AND owner_user_id IS NULL
                   AND base_player_id IN (SELECT base_player_id FROM player_rating WHERE club_id = ? AND game_version_id = 27)""", before.clubId());

        assertThat(statOf(repository.clubStats(GAME, 0, new ManagerProperties(), old.id()), before.clubId()).topValueEur())
                .as("eski sürüm arşivdeki değerlemeyle").isEqualTo(before.topValueEur());
        assertThat(statOf(repository.clubStats(GAME, 0, new ManagerProperties(), null), before.clubId()).topValueEur())
                .as("en yeni sürüm canlı değerlemeyle").isEqualTo(before.topValueEur() * 10);
    }

    @Test
    void clubFitReadsTheArchivedValuationOfTheChosenVersion() {
        long player = jdbc.queryForObject("""
                SELECT a.base_player_id FROM player_rating_archive a JOIN player_valuation_archive v ON v.squad_version_id = a.squad_version_id
                   AND v.base_player_id = a.base_player_id WHERE a.squad_version_id = ? ORDER BY a.base_player_id LIMIT 1""", Long.class, old.id());
        long archived = jdbc.queryForObject("SELECT value_eur FROM player_valuation_archive WHERE squad_version_id = ? AND base_player_id = ?", Long.class, old.id(), player);
        jdbc.update("UPDATE player_valuation SET value_eur = ? WHERE base_player_id = ? AND game_version_id = 27 AND source = 'MODELED' AND owner_user_id IS NULL",
                archived * 10, player);
        entityManager.flush();
        entityManager.clear();

        assertThat(playerService.fitCatalogPlayers(List.of(player), GAME, old.id()).get(0).getValueEur()).as("eski sürüm arşiv değerlemesi").isEqualTo(archived);
        assertThat(playerService.fitCatalogPlayers(List.of(player), GAME, null).get(0).getValueEur()).as("en yeni sürüm canlı").isEqualTo(archived * 10);
    }

    @Test
    void clubFitHasASingleReadPathThroughPlayerService() {
        List<Class<?>> dependencies = java.util.Arrays.stream(ClubFitCalculator.class.getDeclaredFields()).map(java.lang.reflect.Field::getType)
                .collect(java.util.stream.Collectors.toList());

        assertThat(dependencies).as("arşiv kopyası kalktı: katalog okuması yalnız PlayerService üzerinden")
                .doesNotContain(com.fcareer.catalog.repository.BasePlayerRepository.class, com.fcareer.catalog.repository.PlayerValuationRepository.class,
                        com.fcareer.catalog.mapper.PlayerMapper.class);
    }

    private static ClubStat statOf(List<ClubStat> stats, long clubId) {
        return stats.stream().filter((s) -> s.clubId() == clubId).findFirst().orElseThrow();
    }
}
