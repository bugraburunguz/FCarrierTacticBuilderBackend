package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ENG-01: kariyer modunda nitelik, kulüp, lig ve değer filtreleri kariyer verisinden gelir, base katalogdan değil. */
@IntegrationTest
class CareerModeSearchIntegrationTest {

    private static final int CAREER_TEAM_ID = 880_001;

    @Autowired
    private PlayerSearchRepository search;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    private long careerId;
    private long userId;
    private long playerId;
    private int catalogClub;
    private int catalogLeague;
    private int careerClub;
    private int careerLeague;

    @BeforeEach
    void setUp() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        jdbc.update("UPDATE club SET ea_team_id = NULL WHERE ea_team_id = ?", CAREER_TEAM_ID);
        userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, subscription_type) VALUES (?, 'x', 'FREE') RETURNING id", Long.class, "eng01-" + System.nanoTime() + "@example.com");
        Map<String, Object> player = jdbc.queryForMap("""
                SELECT r.base_player_id AS id, r.club_id AS club, c.league_id AS league FROM player_rating r JOIN club c ON c.id = r.club_id
                WHERE r.game_version_id = 27 AND c.league_id IS NOT NULL ORDER BY r.base_player_id LIMIT 1""");
        playerId = ((Number) player.get("id")).longValue();
        catalogClub = ((Number) player.get("club")).intValue();
        catalogLeague = ((Number) player.get("league")).intValue();
        Map<String, Object> other = jdbc.queryForMap("SELECT id, league_id FROM club WHERE league_id IS NOT NULL AND league_id <> ? ORDER BY id LIMIT 1", catalogLeague);
        careerClub = ((Number) other.get("id")).intValue();
        careerLeague = ((Number) other.get("league_id")).intValue();
        jdbc.update("UPDATE club SET ea_team_id = ? WHERE id = ?", CAREER_TEAM_ID, careerClub);
        jdbc.update("UPDATE player_rating SET finishing = 50, weak_foot = 2, skill_moves = 2 WHERE base_player_id = ? AND game_version_id = 27", playerId);

        long clubForCareer = catalogClub;
        careerId = jdbc.queryForObject("INSERT INTO career (user_id, club_id, game_version_id, season) VALUES (?, ?, 27, 1) RETURNING id", Long.class, userId, clubForCareer);
        jdbc.update("INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id) VALUES (?, ?, 70, 80, ?)", careerId, playerId, CAREER_TEAM_ID);
        jdbc.update("INSERT INTO career_player_rating (career_id, base_player_id, game_version_id, club_id, overall, positions, finishing, weak_foot, skill_moves) VALUES (?, ?, 27, ?, 70, ARRAY['ST'], 99, 5, 5)",
                careerId, playerId, catalogClub);
    }

    private List<Long> ids(PlayerSearchRequest request) {
        request.setGameVersionId((short) 27);
        request.setSize(500);
        return search.search(request).stream().map(PlayerSummaryResponse::getId).toList();
    }

    @Test
    void attributeFiltersReadTheCareersRatingNotTheCatalogs() {
        PlayerSearchRequest inCareer = PlayerSearchRequest.builder().careerId(careerId).attrMin(Map.of("finishing", 98)).build();
        PlayerSearchRequest inCatalog = PlayerSearchRequest.builder().attrMin(Map.of("finishing", 98)).build();

        assertThat(ids(inCareer)).contains(playerId);
        assertThat(ids(inCatalog)).doesNotContain(playerId);
    }

    @Test
    void weakFootAndSkillMovesFiltersUseTheCareerOverlay() {
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).weakFootMin(5).skillMovesMin(5).build())).contains(playerId);
        assertThat(ids(PlayerSearchRequest.builder().weakFootMin(5).skillMovesMin(5).build())).doesNotContain(playerId);
    }

    @Test
    void clubAndLeagueFiltersFollowTheCareerTeamNotTheStaleCatalogClub() {
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).clubId(careerClub).build())).contains(playerId);
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).clubId(catalogClub).build())).doesNotContain(playerId);
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).leagueId(careerLeague).build())).contains(playerId);
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).leagueId(catalogLeague).build())).doesNotContain(playerId);
        assertThat(ids(PlayerSearchRequest.builder().careerId(careerId).leagueIds(List.of(careerLeague)).build())).contains(playerId);
    }

    @Test
    void valueFiltersUseTheCareersOwnValueWhenThereIsOne() {
        jdbc.update("UPDATE player_valuation SET value_eur = 90000000 WHERE base_player_id = ? AND source = 'MODELED'", playerId);
        jdbc.update("INSERT INTO player_valuation (base_player_id, game_version_id, source, owner_user_id, value_eur) VALUES (?, 27, 'IMPORT', ?, 120000)", playerId, userId);

        PlayerSearchRequest inCareer = PlayerSearchRequest.builder().careerId(careerId).valueMax(200_000L).build();
        List<PlayerSummaryResponse> found = search.search(withDefaults(inCareer)).stream().filter(p -> p.getId() == playerId).toList();

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getValueEur()).isEqualTo(120_000L);
        assertThat(ids(PlayerSearchRequest.builder().valueMax(200_000L).build())).doesNotContain(playerId);
    }

    private static PlayerSearchRequest withDefaults(PlayerSearchRequest request) {
        request.setGameVersionId((short) 27);
        request.setSize(500);
        return request;
    }
}
