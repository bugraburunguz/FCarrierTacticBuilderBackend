package com.fcareer.development.track;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.AdminTestSupport;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * GTR-a: kariyer içi içe aktarma anlık görüntü yazar (export'taki bütün kulüpler). Aynı oyun ayında yeniden içe aktarma görüntüyü değiştirir; kariyer
 * başına en çok 60; kariyer silinince görüntüler de silinir. Ölçüm raporu yalnız admin. İki görüntü için aynı export iki farklı tarihle içe aktarılır
 * (ilk görüntünün tarihi bir yıl geri alınır).
 */
@IntegrationTest
class GrowthTrackIntegrationTest {

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
    private final List<Object[]> careers = new ArrayList<>();

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    /** Üretilmiş oyuncular ortak test veritabanında kalmasın (AcademyIntegrationTest ile aynı kural). */
    @AfterEach
    void deleteCareers() throws Exception {
        for (Object[] c : careers) {
            mockMvc.perform(delete("/api/v1/careers/" + c[1]).header("Authorization", "Bearer " + c[0])).andReturn();
        }
        careers.clear();
    }

    private void importFixture(String token, long careerId) throws Exception {
        byte[] log = new ClassPathResource("le-export/academy-portvale.log").getInputStream().readAllBytes();
        support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "career.log", "text/plain", log)), token);
    }

    private int snapshots(long careerId) {
        return jdbc.queryForObject("SELECT count(*) FROM career_growth_snapshot WHERE career_id = ?", Integer.class, careerId);
    }

    @Test
    void importsWriteMonthlySnapshotsAndTheAdminReportComparesThem() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careers.add(new Object[] {token, careerId});
        importFixture(token, careerId);
        assertThat(snapshots(careerId)).isEqualTo(1);
        int players = jdbc.queryForObject("SELECT player_count FROM career_growth_snapshot WHERE career_id = ?", Integer.class, careerId);
        int squad = jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ?", Integer.class, careerId);
        assertThat(players).as("bütün kulüpler, yalnız kadro değil").isGreaterThan(squad);

        // Aynı oyun ayı: yalnız sonuncusu tutulur.
        importFixture(token, careerId);
        assertThat(snapshots(careerId)).isEqualTo(1);

        // İki görüntü: ilkinin tarihi bir yıl geri.
        jdbc.update("UPDATE career_growth_snapshot SET game_date = game_date - INTERVAL '1 year', month_key = month_key - 100 WHERE career_id = ?", careerId);
        importFixture(token, careerId);
        assertThat(snapshots(careerId)).isEqualTo(2);

        String admin = AdminTestSupport.adminToken(mockMvc, jdbc, objectMapper);
        var response = mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").header("Authorization", "Bearer " + admin)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        JsonNode report = objectMapper.readTree(response.getContentAsString()).at("/data");
        assertThat(report.path("matched").asInt()).isEqualTo(players);
        assertThat(report.path("partial").asBoolean()).isFalse();
        assertThat(report.at("/groups/age").size()).isGreaterThan(0);
        assertThat(report.at("/groups/age/0/n").asInt()).isGreaterThan(0);
        assertThat(report.at("/groups/play/0/key").asText()).isNotBlank();
        // Aynı export: gerçek değişim 0, model tahmini ve fark yazılı.
        assertThat(report.at("/groups/age/0/meanOverallDelta").asDouble()).isEqualTo(0.0);
        assertThat(report.at("/groups/age/0").has("meanPredicted")).isTrue();
        writeSample(report);

        var csv = mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").param("format", "csv").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse();
        assertThat(csv.getStatus()).isEqualTo(200);
        assertThat(csv.getContentType()).startsWith("text/csv");
        assertThat(csv.getContentAsString()).startsWith("boyut,grup,n,");

        // Yalnız admin.
        assertThat(mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").header("Authorization", "Bearer " + token)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void theCapIsSixtyAndDeletingTheCareerRemovesSnapshots() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careers.add(new Object[] {token, careerId});
        for (int m = 0; m < 60; m++) {
            jdbc.update("INSERT INTO career_growth_snapshot (career_id, game_date, month_key, player_count, data) VALUES (?, DATE '2020-01-01' + (? || ' month')::interval, ?, 0, ?)",
                    careerId, String.valueOf(m), 202001 + (m / 12) * 100 + m % 12, GrowthSnapshotCodec.encode(List.of()));
        }
        importFixture(token, careerId);
        assertThat(snapshots(careerId)).as("en çok 60").isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT max(game_date)::text FROM career_growth_snapshot WHERE career_id = ?", String.class, careerId)).isEqualTo("2027-09-19");
        assertThat(jdbc.queryForObject("SELECT min(game_date)::text FROM career_growth_snapshot WHERE career_id = ?", String.class, careerId)).as("ilk görüntü korunur")
                .isEqualTo("2020-01-01");

        mockMvc.perform(delete("/api/v1/careers/" + careerId).header("Authorization", "Bearer " + token)).andReturn();
        careers.clear();
        assertThat(snapshots(careerId)).isZero();
    }

    /** Rapor örneği (GTR-a kabulü): boş olmayan çıktı target/'a yazılır. */
    private static void writeSample(JsonNode report) throws java.io.IOException {
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/gtr-a-report-sample.json"), report.toPrettyString());
    }
}
