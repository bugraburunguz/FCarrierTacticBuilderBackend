package com.fcareer.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * ACADEMY-b kararları (2026-10-25): (2) oyuncusu olmayan kulüpler (Klasik Lig, futsal, 5'e 5 …) silinmez ama takım ve lig listelerinden gizlenir;
 * yalnız boş kulüpleri olan lig de listede yok. Oyuncu = katalog oyuncusu (kariyerin ürettiği oyuncu başka kullanıcıya görünmez).
 * (3) çözülmemiş lig adı (`*LeagueName_…`) "Lig #id" olarak gösterilir.
 */
@IntegrationTest
class CatalogListsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    private JsonNode data(String path) throws Exception {
        var response = mockMvc.perform(get(path)).andReturn().getResponse();
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    private static List<String> names(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach((i) -> out.add(i.path("name").asText()));
        return out;
    }

    @Test
    void clubsWithoutCatalogPlayersAreHiddenFromClubAndLeagueLists() throws Exception {
        Integer emptyLeague = jdbc.queryForObject("INSERT INTO league (name, ea_league_id) VALUES ('ACB Klasik Lig', 92255) RETURNING id", Integer.class);
        jdbc.update("INSERT INTO club (name, league_id, gender, ea_team_id) VALUES ('ACB Classic XI', ?, 0, 9132670)", emptyLeague);
        // Yalnız kariyerin ürettiği oyuncusu olan kulüp de boş sayılır (üretilmiş oyuncu herkese açık listeye sızmaz).
        Long career = jdbc.queryForObject("SELECT id FROM career ORDER BY id LIMIT 1", Long.class);
        Integer generatedOnly = jdbc.queryForObject("INSERT INTO club (name, league_id, gender) VALUES ('ACB Üretilmiş FC', ?, 0) RETURNING id", Integer.class, emptyLeague);
        if (career != null) {
            Long generated = jdbc.queryForObject("INSERT INTO base_player (full_name, owner_career_id) VALUES ('ACB Genç', ?) RETURNING id", Long.class, career);
            jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, club_id, overall, positions) VALUES (?, 27, ?, 50, '{ST}')", generated, generatedOnly);
        }

        assertThat(names(data("/api/v1/clubs?q=ACB&limit=50"))).isEmpty();
        assertThat(names(data("/api/v1/leagues"))).doesNotContain("ACB Klasik Lig");
        // Satırlar silinmez.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club WHERE name LIKE 'ACB %'", Integer.class)).isEqualTo(2);

        // Oyuncusu olan kulüp ve ligi görünür.
        Long catalogPlayer = jdbc.queryForObject("INSERT INTO base_player (full_name) VALUES ('ACB Oyuncu') RETURNING id", Long.class);
        Integer full = jdbc.queryForObject("INSERT INTO club (name, league_id, gender) VALUES ('ACB Dolu FC', ?, 0) RETURNING id", Integer.class, emptyLeague);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, club_id, overall, positions) VALUES (?, 27, ?, 60, '{CM}')", catalogPlayer, full);
        assertThat(names(data("/api/v1/clubs?q=ACB&limit=50"))).containsExactly("ACB Dolu FC");
        assertThat(names(data("/api/v1/leagues"))).contains("ACB Klasik Lig");
    }

    @Test
    void unresolvedLeagueNameFallsBackToLigId() throws Exception {
        Integer league = jdbc.queryForObject("INSERT INTO league (name, ea_league_id) VALUES ('*LeagueName_Abbr15_92240', 92240) RETURNING id", Integer.class);
        Integer club = jdbc.queryForObject("INSERT INTO club (name, league_id, gender) VALUES ('ACL 5''e 5 Takımı', ?, 0) RETURNING id", Integer.class, league);
        Long player = jdbc.queryForObject("INSERT INTO base_player (full_name) VALUES ('ACL Oyuncu') RETURNING id", Long.class);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, club_id, overall, positions) VALUES (?, 27, ?, 60, '{CM}')", player, club);

        assertThat(names(data("/api/v1/leagues"))).contains("Lig #92240").noneMatch((n) -> n.startsWith("*"));
        assertThat(data("/api/v1/clubs?q=ACL&limit=5").get(0).path("league").asText()).isEqualTo("Lig #92240");
    }

    @Test
    void displayLeagueNameRule() {
        assertThat(BaseCatalogImporter.displayLeagueName("*LeagueName_Abbr15_2240", 2240)).isEqualTo("Lig #2240");
        assertThat(BaseCatalogImporter.displayLeagueName("Premier League", 13)).isEqualTo("Premier League");
        assertThat(BaseCatalogImporter.displayLeagueName(null, 5)).isNull();
    }
}
