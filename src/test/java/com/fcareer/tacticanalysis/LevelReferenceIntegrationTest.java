package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-7b: seviye referansı gerçekten veriden gelir: kariyerde ligin aynı mevki medyanı (SQL ↔ Attr sırası), kariyer dışında OVR bandının katalog medyanı. */
@IntegrationTest
@TestPropertySource(properties = "fcareer.tactic.level-min-sample=1")
class LevelReferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LevelReferenceRepository repository;

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    @Test
    void theLeagueMedianOfCentreBackPaceMatchesTheAttributesOfTheLeaguesCentreBacks() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        support.json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/players?career=" + careerId + "&size=5"), token); // career_player katalogdan tohumlanır
        jdbc.update("INSERT INTO career_team (career_id, team_id, name, league_name, gender, free_agent_pool) VALUES (?, 99999, 'Test FC', 'Test League', 0, false) ON CONFLICT DO NOTHING", careerId);
        List<Long> ids = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ? AND positions[1] = 'CB' ORDER BY player_id LIMIT 9", Long.class, careerId);
        assertThat(ids).hasSizeGreaterThanOrEqualTo(5);
        jdbc.update("UPDATE career_player SET live = true, team_id = 99999, free_agent = false WHERE career_id = ? AND player_id IN (" + ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow() + ")", careerId);

        List<Double> paces = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> row = jdbc.queryForMap("SELECT attrs[" + (Attr.ACCELERATION.ordinal() + 1) + "] AS a, attrs[" + (Attr.SPRINT_SPEED.ordinal() + 1) + "] AS s FROM career_player WHERE career_id = ? AND player_id = ?", careerId, id);
            paces.add((((Number) row.get("a")).doubleValue() + ((Number) row.get("s")).doubleValue()) / 2.0);
        }
        paces.sort(Double::compare);
        double expected = paces.size() % 2 == 1 ? paces.get(paces.size() / 2) : (paces.get(paces.size() / 2 - 1) + paces.get(paces.size() / 2)) / 2.0;

        var m = repository.leagueMedians(careerId, "Test League", 165, 195);
        assertThat(m.cbPace().n()).isEqualTo(ids.size());
        assertThat(m.cbPace().value()).isEqualTo(expected, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void outsideACareerTheReferenceIsTheCatalogMedianOfTheTeamsOvrBand() throws Exception {
        String token = support.register();
        List<Long> ids = jdbc.queryForList("SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE b.owner_career_id IS NULL AND b.retired_at IS NULL AND b.gender = 0 ORDER BY r.base_player_id LIMIT 11", Long.class);
        Map<String, Object> assigned = new java.util.LinkedHashMap<>();
        List<String> slotIds = List.of("GK", "LB", "LCB", "RCB", "RB", "LDM", "RDM", "LM", "CAM", "RM", "ST");
        for (int i = 0; i < slotIds.size(); i++) {
            assigned.put(slotIds.get(i), ids.get(i));
        }
        JsonNode r = support.json(post("/api/v1/tactics/analysis").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("formation", "4-2-3-1 (2)", "buildUp", "Short", "depth", 80, "assigned", assigned))), token);
        assertThat(r.at("/success").asBoolean()).as(r.toString()).isTrue();
        assertThat(r.at("/data/levelLabel").asText()).contains("katalog bandı");
        assertThat(r.at("/data/metrics/stamina/supply").isNumber()).as("kariyer dışı katalog oyuncuları da birleşir").isTrue();
    }
}
