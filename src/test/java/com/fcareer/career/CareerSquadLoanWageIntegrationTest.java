package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
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

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * KARIYER-UX FAZ 2 (15 §7.3): kadro satırı kayıttaki kiralık bitişini (loanUntil) ve maaşı (wageEur) taşır; Masa uyarıları /moves'a bağlı kalmaz,
 * "N oyuncunun maaşı bilinmiyor" sayılabilir. Bilinmeyen alan yanıtta yoktur.
 */
@IntegrationTest
class CareerSquadLoanWageIntegrationTest {

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

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void squadRowsCarryLoanUntilAndWageFromTheRecord() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        careerPlayerWriter.syncCareer(careerId);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        jdbc.update("UPDATE career_player SET live = true, loan_until = ?, wage_eur = 12000 WHERE career_id = ? AND player_id = ?",
                Date.valueOf(LocalDate.of(2027, 1, 31)), careerId, squad.get(0));
        jdbc.update("UPDATE career_player SET live = true, wage_eur = 8000 WHERE career_id = ? AND player_id = ?", careerId, squad.get(1));

        JsonNode rows = support.json(get("/api/v1/careers/" + careerId + "/squad").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null).at("/data");
        Map<Long, JsonNode> byId = new HashMap<>();
        rows.forEach(r -> byId.put(r.at("/player/id").asLong(), r));

        assertThat(byId.get(squad.get(0)).at("/loanUntil").asText()).isEqualTo("2027-01-31");
        assertThat(byId.get(squad.get(0)).at("/wageEur").asLong()).isEqualTo(12_000L);
        assertThat(byId.get(squad.get(1)).has("loanUntil")).isFalse();
        assertThat(byId.get(squad.get(1)).at("/wageEur").asLong()).isEqualTo(8_000L);
        assertThat(byId.get(squad.get(2)).has("wageEur")).as("bilinmeyen maaş yanıtta yok").isFalse();
    }
}
