package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * GT-a raporu (opt-in, varsayılan atlanır): kariyer içi Live Editor export'unu içe aktarır, Gelişim'in tüm takım planından 30+ oyuncuları ve panel
 * karşılaştırmasını (Dengeli ve önerilen plan, 1 ve 2 sezon sonraki mevki OVR) target/gt-a-veterans.json'a yazar.
 * Kullanım: mvn test -Dtest=VeteranPlanReportTest -Dgta.report=/mutlak/yol/live_editor_..._in_career.log
 */
@IntegrationTest
@EnabledIfSystemProperty(named = "gta.report", matches = ".+")
class VeteranPlanReportTest {

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

    @Test
    void writesTheVeteranPlanReport() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        byte[] log = Files.readAllBytes(Path.of(System.getProperty("gta.report")));
        try {
            support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "career.log", "text/plain", log)), token);
            JsonNode training = data(token, "/api/v1/careers/" + careerId + "/training");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode p : training) {
                if (p.path("age").asInt() < 30) {
                    continue;
                }
                JsonNode panel = data(token, "/api/v1/careers/" + careerId + "/youth/" + p.path("playerId").asLong());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", p.path("name").asText());
                row.put("age", p.path("age").asInt());
                row.put("position", p.path("bestPosition").asText(p.at("/positions/0").asText()));
                row.put("overall", p.path("overall").asInt());
                row.put("plan", p.at("/plan/label").asText());
                row.put("text", p.at("/plan/text").asText());
                row.put("sentence", panel.at("/advice/sentence").asText());
                row.put("plans", panel.at("/advice/plans"));
                rows.add(row);
            }
            Files.writeString(Path.of("target/gt-a-veterans.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
            assertThat(rows).isNotEmpty();
        } finally {
            mockMvc.perform(delete("/api/v1/careers/" + careerId).header("Authorization", "Bearer " + token)).andReturn();
        }
    }

    private JsonNode data(String token, String path) throws Exception {
        var response = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }
}
