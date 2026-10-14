package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-7: `POST /api/v1/tactics/analysis`: etiketsiz taktik (formasyon + rol/focus + build-up + hat) bulgu üretir; atanan oyuncu varsa attribute'larla birleşir. */
@IntegrationTest
class TacticAnalysisIntegrationTest {

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

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode analyse(String token, Map<String, Object> body) throws Exception {
        return support.json(post("/api/v1/tactics/analysis").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), token);
    }

    @Test
    void aTacticWithoutAnyTagProducesFindingsWithNumericEvidence() throws Exception {
        String token = support.register();
        JsonNode r = analyse(token, Map.of("formation", "4-2-3-1 (2)", "buildUp", "Balanced", "depth", 55,
                "slots", List.of(Map.of("slotId", "LM", "roleId", "inside_forward_attack"), Map.of("slotId", "LB", "roleId", "fullback_defend"))));
        assertThat(r.at("/success").asBoolean()).as(r.toString()).isTrue();
        assertThat(r.at("/data/metrics/band").asText()).isEqualTo("BALANCED");
        JsonNode finding = null;
        for (JsonNode f : r.at("/data/findings")) {
            if (f.path("code").asText().equals("corridor_no_width")) {
                finding = f;
            }
        }
        assertThat(finding).isNotNull();
        assertThat(finding.at("/instance/side").asText()).isEqualTo("LEFT");
        assertThat(finding.at("/evidence/0/value").asDouble()).isGreaterThanOrEqualTo(finding.at("/evidence/0/threshold").asDouble());
        assertThat(r.at("/data/notEvaluated").toString()).contains("counter_risk");
    }

    @Test
    void unknownFormationOrRoleIsRejected() throws Exception {
        String token = support.register();
        assertThat(analyse(token, Map.of("formation", "9-9-9")).at("/success").asBoolean()).isFalse();
        JsonNode bad = analyse(token, Map.of("formation", "4-3-3", "slots", List.of(Map.of("slotId", "ST", "roleId", "no_such_role"))));
        assertThat(bad.at("/success").asBoolean()).isFalse();
    }

    @Test
    void assignedCareerPlayersMergeWithTheirAttributesAndPlayerRulesAreEvaluated() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        List<Long> squad = jdbc.queryForList("SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE b.owner_career_id IS NULL AND b.retired_at IS NULL AND b.gender = 0 ORDER BY r.base_player_id LIMIT 11", Long.class);
        Map<String, Object> assigned = new java.util.LinkedHashMap<>();
        List<String> slotIds = List.of("GK", "LB", "LCB", "RCB", "RB", "LDM", "RDM", "LM", "CAM", "RM", "ST");
        for (int i = 0; i < slotIds.size() && i < squad.size(); i++) {
            assigned.put(slotIds.get(i), squad.get(i));
        }
        JsonNode r = analyse(token, Map.of("formation", "4-2-3-1 (2)", "buildUp", "Short", "depth", 80, "careerId", careerId, "assigned", assigned));
        assertThat(r.at("/success").asBoolean()).as(r.toString()).isTrue();
        assertThat(r.at("/data/metrics/stamina/supply").isNumber()).isTrue();
        assertThat(r.at("/data/notEvaluated").toString()).doesNotContain("counter_risk");
    }

    @Test
    void aForeignCareerIsNotFound() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        String other = support.register();
        JsonNode r = analyse(other, Map.of("formation", "4-3-3", "careerId", careerId, "assigned", Map.of("ST", 1)));
        assertThat(r.at("/success").asBoolean()).isFalse();
    }
}
