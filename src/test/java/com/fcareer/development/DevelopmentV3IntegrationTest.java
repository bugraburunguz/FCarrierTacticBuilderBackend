package com.fcareer.development;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.roles.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-6: DevelopmentService kariyerde v3'e geçer. currentScore = FitEngineV3.currentFit; projectedScore/projectedRating = GrowthProjector (+1 sezon) üzerinden. */
@IntegrationTest
class DevelopmentV3IntegrationTest {

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
    private PlayerService playerService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private FitEngineV3 engine;
    @Autowired
    private GrowthProjector growthProjector;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void careerDevelopmentPlanUsesTheV3EngineAndTheGrowthProjection() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        support.json(get("/api/v1/players?career=" + careerId + "&size=1"), token);
        Long playerId = jdbc.queryForObject("SELECT player_id FROM career_player WHERE career_id = ? AND NOT ('GK' = ANY(positions)) ORDER BY player_id LIMIT 1", Long.class, careerId);
        jdbc.update("UPDATE career_player SET pot = LEAST(99, ovr + 10) WHERE career_id = ? AND player_id = ?", careerId, playerId);
        jdbc.update("UPDATE base_player SET dob = CURRENT_DATE - INTERVAL '19 years' WHERE id = ?", playerId);
        assertThat(playerId).as("büyüme payı olan oyuncu (ön koşul)").isNotNull();
        JsonNode response = support.json(post("/api/v1/players/" + playerId + "/development").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("careerId", careerId))), token);
        assertThat(response.at("/success").asBoolean()).as(response.toString()).isTrue();
        JsonNode perRole = response.at("/data/perRole");
        assertThat(perRole.size()).isPositive();

        FitPlayer player = playerService.fitPlayers(List.of(playerId), (short) 27, careerId).get(0);
        GrowthProjector.Projection projection = growthProjector.project(player);
        Set<String> expectedIds = Set.copyOf(roleService.fitRolesById().keySet());
        for (JsonNode entry : perRole) {
            FitRole role = roleService.fitRolesById().get(entry.path("roleId").asText());
            assertThat(expectedIds).contains(role.getId());
            String position = entry.path("position").asText();
            assertThat(entry.path("currentScore").asDouble()).isEqualTo(Math.round(engine.currentFit(player, position, role).currentFit() * 10.0) / 10.0);
            assertThat(entry.path("projectedScore").asDouble()).isEqualTo(Math.round(engine.currentFit(projection.grown(), position, role).currentFit() * 10.0) / 10.0);
            assertThat(entry.path("projectedRating").asInt()).as("projectedRating = sezon sonu beklenen OVR").isEqualTo(projection.projectedRating());
        }
    }
}
