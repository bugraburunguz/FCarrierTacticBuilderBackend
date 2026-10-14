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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-7: eski etiket tabanlı kurallar kalktı; `/fit/squad` yanıtındaki `rules` yeni analiz motorundan (rol hareketi + oyuncu attribute'ları) gelir, kanıt sayılarıyla. */
@IntegrationTest
class SquadFitRulesIntegrationTest {

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

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    @Test
    void squadFitRulesComeFromTheAnalysisEngineWithNumericEvidence() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Map<String, Object> body = Map.of("careerId", careerId, "tactic", Map.of("formation", "4-2-3-1 (2)", "setup", Map.of("buildUp", "Balanced", "depth", 55),
                "slots", List.of(Map.of("slotId", "LM", "roleId", "inside_forward_attack"), Map.of("slotId", "LB", "roleId", "fullback_defend"))));
        JsonNode r = support.json(post("/api/v1/fit/squad").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), token);
        assertThat(r.at("/success").asBoolean()).as(r.toString()).isTrue();
        JsonNode rules = r.at("/data/result/rules");
        assertThat(rules.isArray()).isTrue();
        JsonNode corridor = null;
        for (JsonNode f : rules) {
            if (f.path("ruleId").asText().startsWith("corridor_no_width")) {
                corridor = f;
            }
            assertThat(f.path("title").asText()).as("başlık").isNotBlank();
            assertThat(f.path("evidence").isArray()).as(f.path("ruleId").asText()).isTrue();
        }
        assertThat(corridor).as("sol koridor bulgusu: " + rules).isNotNull();
        assertThat(corridor.path("message").asText()).contains("sol");
        assertThat(rules.toString()).doesNotContain("kontra_riski_yuksek");
    }
}
