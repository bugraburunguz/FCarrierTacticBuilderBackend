package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.career.desk.CareerDeskService;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.base.CareerPlayerWriter;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * KARIYER-UX FAZ 2 (15 §7.2): Masa'nın finans kutucukları önceki IMPORT snapshot'ına göre Δ gösterir; snapshot o anki transfer bütçesini ve
 * kadro maaş toplamını saklar. Kadroda maaşı bilinmeyen oyuncu varsa toplam yoktur (yanlış Δ yerine hiç Δ).
 */
@IntegrationTest
class SnapshotFinanceIntegrationTest {

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
    private CareerPlayerWriter careerPlayerWriter;
    @Autowired
    private CareerDeskService careerDeskService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void snapshotRecordsBudgetAndSquadWageOnlyWhenEveryWageIsKnown() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careerPlayerWriter.syncCareer(careerId);
        jdbc.update("UPDATE career SET budget_eur = 25000000 WHERE id = ?", careerId);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        jdbc.update("UPDATE career_player SET wage_eur = NULL WHERE career_id = ?", careerId);
        jdbc.update("UPDATE career_player SET wage_eur = 10000 WHERE career_id = ? AND player_id = ?", careerId, squad.get(0));
        careerDeskService.takeSnapshot(careerId, "IMPORT", null);

        jdbc.update("UPDATE career_player SET wage_eur = 1000 WHERE career_id = ? AND player_id <> ?", careerId, squad.get(0));
        careerDeskService.takeSnapshot(careerId, "IMPORT", null);

        JsonNode snapshots = support.json(get("/api/v1/careers/" + careerId + "/snapshots").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null).at("/data");
        assertThat(snapshots.get(0).at("/budgetEur").asLong()).isEqualTo(25_000_000L);
        assertThat(snapshots.get(0).at("/squadWageEur").asLong()).isEqualTo(10_000L + 1_000L * (squad.size() - 1));
        assertThat(snapshots.get(1).at("/budgetEur").asLong()).isEqualTo(25_000_000L);
        assertThat(snapshots.get(1).has("squadWageEur")).as("maaşı bilinmeyen oyuncu varken toplam yok").isFalse();
    }
}
