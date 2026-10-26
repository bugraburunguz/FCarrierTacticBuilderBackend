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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private com.fcareer.catalog.service.PlayerService playerService;
    @Autowired
    private com.fcareer.development.recommend.DevelopmentRecommender recommender;
    @Autowired
    private com.fcareer.development.plan.PlanProjector projector;
    @Autowired
    private com.fcareer.development.plan.DevelopmentPlans plans;

    /** Ana mevki grubu (rapor özeti): CB, bek (LB/RB/LWB/RWB), orta saha (CDM/CM/CAM), kanat (LM/RM/LW/RW), ST (ST/CF); kaleci ayrı. */
    private static String positionGroup(String position) {
        return switch (position) {
            case "CB" -> "CB";
            case "LB", "RB", "LWB", "RWB" -> "Bek";
            case "CDM", "CM", "CAM" -> "CM";
            case "LM", "RM", "LW", "RW" -> "Kanat";
            case "ST", "CF" -> "ST";
            default -> "GK";
        };
    }

    private static String ageBand(int age) {
        return age <= 31 ? "30-31" : age <= 33 ? "32-33" : "34+";
    }

    /**
     * Export'taki bütün kulüplerin 30+ oyuncuları (kariyer dünyası, career_player): tecrübeli kuralının önerdiği planla 1 sezon sonraki kart OVR kaybı,
     * plansız (Dengeli) ile kıyas. Yalnız özet: yaş × mevki ortalamaları, en çok/az kaybeden 5, sağlamalar. target/gt-a-all-veterans.json.
     */
    @Test
    void writesTheAllClubsVeteranSummary() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        byte[] log = Files.readAllBytes(Path.of(System.getProperty("gta.report")));
        try {
            support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "career.log", "text/plain", log)), token);
            long userId = jdbc.queryForObject("SELECT user_id FROM career WHERE id = ?", Long.class, careerId);
            short gv = jdbc.queryForObject("SELECT game_version_id FROM career WHERE id = ?", Short.class, careerId);
            List<Long> ids = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ?", Long.class, careerId);
            int teams = jdbc.queryForObject("SELECT count(DISTINCT team_id) FROM career_player WHERE career_id = ?", Integer.class, careerId);
            List<com.fcareer.fitengine.model.FitPlayer> veterans = playerService.fitPlayers(ids, gv, careerId).stream()
                    .filter((p) -> p.getAge() != null && p.getAge() >= 30 && p.getOverall() != null && !p.getPositions().isEmpty()).toList();
            Map<Long, com.fcareer.development.recommend.DevelopmentRecommender.PlayerRecommendation> recs = new java.util.HashMap<>();
            recommender.recommendFor(userId, careerId, veterans, 0).forEach((r) -> recs.put(r.playerId(), r));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (com.fcareer.fitengine.model.FitPlayer p : veterans) {
                var rec = recs.get(p.getId());
                var plan = rec == null || rec.balanced() || rec.recommended() == null ? plans.general() : plans.byKey(rec.recommended().planKey()).orElse(plans.general());
                int balanced = p.getOverall() - projector.project(p, plans.general(), 1).overall();
                int planned = p.getOverall() - projector.project(p, plan, 1).overall();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", p.getName());
                row.put("age", p.getAge());
                row.put("position", p.getPositions().get(0));
                row.put("overall", p.getOverall());
                row.put("plan", plan.name());
                row.put("balancedLoss", balanced);
                row.put("plannedLoss", planned);
                rows.add(row);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("teams", teams);
            summary.put("players", ids.size());
            summary.put("veterans", rows.size());
            Map<String, double[]> cells = new java.util.TreeMap<>();
            rows.forEach((r) -> {
                double[] c = cells.computeIfAbsent(ageBand((Integer) r.get("age")) + " " + positionGroup((String) r.get("position")), (k) -> new double[3]);
                c[0]++;
                c[1] += (Integer) r.get("balancedLoss");
                c[2] += (Integer) r.get("plannedLoss");
            });
            Map<String, String> table = new LinkedHashMap<>();
            cells.forEach((k, c) -> table.put(k, String.format(java.util.Locale.ROOT, "n=%d plansız %.2f / planlı %.2f", (int) c[0], c[1] / c[0], c[2] / c[0])));
            summary.put("cells", table);
            java.util.Comparator<Map<String, Object>> byPlanned = java.util.Comparator.comparingInt((Map<String, Object> r) -> (Integer) r.get("plannedLoss"));
            summary.put("mostLoss", rows.stream().sorted(byPlanned.reversed()).limit(5).toList());
            summary.put("leastLoss", rows.stream().sorted(byPlanned).limit(5).toList());
            summary.put("gains", rows.stream().filter((r) -> (Integer) r.get("plannedLoss") < 0 || (Integer) r.get("balancedLoss") < 0).toList());
            summary.put("plannedWorse", rows.stream().filter((r) -> (Integer) r.get("plannedLoss") > (Integer) r.get("balancedLoss")).toList());
            Files.writeString(Path.of("target/gt-a-all-veterans.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
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
