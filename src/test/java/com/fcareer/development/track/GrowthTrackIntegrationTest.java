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

        // Yalnız admin; oturumsuz istek reddedilir.
        assertThat(mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").header("Authorization", "Bearer " + token)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report")).andReturn().getResponse().getStatus()).isEqualTo(401);
        // Tek görüntülük aralık ya da ters aralık: rapor yok (404).
        assertThat(mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").param("from", "2027-01-01").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/admin/careers/" + careerId + "/growth-report").param("from", "2027-09-01").param("to", "2026-01-01")
                .header("Authorization", "Bearer " + admin)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    /** Görüntü yalnız ölçüm içindir: yazılamazsa (ör. gövde bozuk) içe aktarma yine başarılı. */
    @Test
    void aSnapshotFailureDoesNotAbortTheImport() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careers.add(new Object[] {token, careerId});
        // NOT VALID: ortak test veritabanındaki mevcut satırlar denetlenmez, yalnız bu içe aktarmanın yazımı reddedilir.
        jdbc.execute("ALTER TABLE career_growth_snapshot ADD CONSTRAINT growth_test_block CHECK (player_count < 0) NOT VALID");
        try {
            importFixture(token, careerId);
            assertThat(snapshots(careerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player_data WHERE career_id = ?", Integer.class, careerId)).isPositive();
        } finally {
            jdbc.execute("ALTER TABLE career_growth_snapshot DROP CONSTRAINT growth_test_block");
        }
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

    /**
     * GTR-b: oyuncunun Gelişim satırı. Tek görüntüde liste boş. İkinci görüntüde taban 3 ay önce; tabandaki OVR'ler 2 düşürülür, her kadro oyuncusu
     * +2 OVR alır. Maç tablosu bu export'ta yok: maç null. Kimlikler kadro (base_player) kimliği; başkasının kariyeri 404.
     */
    @Test
    void recentChangesPerSquadPlayer() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careers.add(new Object[] {token, careerId});
        importFixture(token, careerId);
        String recent = "/api/v1/careers/" + careerId + "/growth-recent";
        assertThat(support.json(get(recent), token).at("/data").size()).as("tek görüntü").isZero();

        long id = jdbc.queryForObject("SELECT id FROM career_growth_snapshot WHERE career_id = ?", Long.class, careerId);
        List<TrackedPlayer> lower = GrowthSnapshotCodec.decode(jdbc.queryForObject("SELECT data FROM career_growth_snapshot WHERE id = ?", byte[].class, id)).stream()
                .map((p) -> new TrackedPlayer(p.playerId(), p.teamId(), p.age(), p.position(), p.overall() - 2, p.potential(), p.form(), p.leagueApps(),
                        p.teamMatches(), p.matches(), p.attrs())).toList();
        jdbc.update("""
                UPDATE career_growth_snapshot SET game_date = game_date - INTERVAL '3 month', data = ?,
                       month_key = EXTRACT(YEAR FROM game_date - INTERVAL '3 month') * 100 + EXTRACT(MONTH FROM game_date - INTERVAL '3 month') WHERE id = ?""",
                GrowthSnapshotCodec.encode(lower), id);
        importFixture(token, careerId);

        JsonNode rows = support.json(get(recent), token).at("/data");
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ?", Long.class, careerId);
        List<Long> rowIds = new ArrayList<>();
        rows.forEach((r) -> rowIds.add(r.path("playerId").asLong()));
        assertThat(rowIds).as("kadronun büyük kısmı eşleşir").hasSizeGreaterThan(squad.size() / 2);
        assertThat(squad).as("yalnız kadro oyuncuları, base_player kimliğiyle").containsAll(rowIds);
        rows.forEach((r) -> {
            assertThat(r.path("months").asInt()).isEqualTo(3);
            assertThat(r.path("overallDelta").asInt()).isEqualTo(2);
            assertThat(r.path("matches").isNull()).isTrue();
        });

        String other = support.register();
        assertThat(mockMvc.perform(get(recent).header("Authorization", "Bearer " + other)).andReturn().getResponse().getStatus()).isEqualTo(404);

        // Önbellek (career_id, data_version + görüntü listesi ve yazılma zamanı): hiçbiri değişmeden veri değişse de sonuç önbellekten (isabet kanıtı).
        List<TrackedPlayer> higher = lower.stream().map((p) -> new TrackedPlayer(p.playerId(), p.teamId(), p.age(), p.position(), p.overall() + 3, p.potential(), p.form(),
                p.leagueApps(), p.teamMatches(), p.matches(), p.attrs())).toList();
        jdbc.update("UPDATE career_growth_snapshot SET data = ? WHERE id = ?", GrowthSnapshotCodec.encode(higher), id);
        assertThat(support.json(get(recent), token).at("/data/0/overallDelta").asInt()).as("önbellekten").isEqualTo(2);
        // Aynı ay yeniden yazım (upsert created_at = now(); içe aktarma sonra geri alınsa da sürüm artmaz): yeniden hesap.
        jdbc.update("UPDATE career_growth_snapshot SET created_at = now() + INTERVAL '1 second' WHERE id = ?", id);
        assertThat(support.json(get(recent), token).at("/data/0/overallDelta").asInt()).as("yeniden yazılan görüntü: yeniden hesap (taban 3 yüksek → −1)").isEqualTo(-1);
        // Sürüm artınca da yeniden hesap.
        jdbc.update("UPDATE career_growth_snapshot SET data = ? WHERE id = ?", GrowthSnapshotCodec.encode(lower), id);
        jdbc.update("UPDATE career SET data_version = data_version + 1 WHERE id = ?", careerId);
        assertThat(support.json(get(recent), token).at("/data/0/overallDelta").asInt()).as("yeni sürüm: yeniden hesap").isEqualTo(2);
    }

    /** Rapor örneği (GTR-a kabulü): boş olmayan çıktı target/'a yazılır. */
    private static void writeSample(JsonNode report) throws java.io.IOException {
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/gtr-a-report-sample.json"), report.toPrettyString());
    }
}
