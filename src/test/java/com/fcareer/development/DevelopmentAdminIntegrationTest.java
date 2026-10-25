package com.fcareer.development;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.AdminTestSupport;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** FIT-3 madde 8 ve 10: admin raporları (yalnız ADMIN, yalnız okuma). Kalibrasyon iki attribute'lu IMPORT görüntüsü yoksa "veri yok". */
@IntegrationTest
class DevelopmentAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
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

    private JsonNode getJson(String token, String path, int status) throws Exception {
        var response = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return status == 200 ? objectMapper.readTree(response.getContentAsString()).at("/data") : null;
    }

    @Test
    void reportsAreAdminOnly() throws Exception {
        String user = support.register();
        getJson(user, "/api/v1/admin/development/plan-report", 403);
        String admin = AdminTestSupport.adminToken(mockMvc, jdbc, objectMapper);
        assertThat(getJson(admin, "/api/v1/admin/development/plan-report", 200).get("positions")).hasSize(11);
    }

    @Test
    void calibrationNeedsTwoImportsWithAttributes() throws Exception {
        String admin = AdminTestSupport.adminToken(mockMvc, jdbc, objectMapper);
        String user = support.register();
        long careerId = support.premiumCareer(user, null);
        assertThat(getJson(admin, "/api/v1/admin/development/careers/" + careerId + "/growth-calibration", 200).get("status").asText()).isEqualTo("NO_DATA");

        List<Long> players = jdbc.queryForList("SELECT id FROM base_player ORDER BY id LIMIT 2", Long.class);
        for (int i = 0; i < 2; i++) {
            Long snapshot = jdbc.queryForObject("INSERT INTO career_snapshot (career_id, taken_at, source, player_count) VALUES (?, now(), 'IMPORT', 2) RETURNING id",
                    Long.class, careerId);
            for (Long p : players) {
                jdbc.update("INSERT INTO career_snapshot_player (snapshot_id, base_player_id, overall, potential, age, attrs) VALUES (?, ?, ?, 80, 19, ?::jsonb)",
                        snapshot, p, 60 + i * 3, "{\"finishing\": " + (60 + i * 5) + ", \"sprint_speed\": 70, \"weak_foot\": 3}");
            }
        }
        JsonNode report = getJson(admin, "/api/v1/admin/development/careers/" + careerId + "/growth-calibration", 200);
        assertThat(report.get("status").asText()).isEqualTo("DATA");
        assertThat(report.get("players").asInt()).isEqualTo(2);
        assertThat(report.get("meanOverallDelta").asDouble()).isEqualTo(3.0);
        assertThat(report.at("/attributes/0/attr").asText()).isEqualTo("FINISHING");
    }
}
