package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** ENG-03: başka kulübe kiralık verilen oyuncu müsait değildir (ilk 11'e girmez) ama pazarda aday da olmaz (hâlâ bizim). */
@IntegrationTest
class LoanedOutAvailabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void loanedOutPlayersNeverStartButStayOwned() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "loan-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", clubRepository.findAll().get(0).getId(), "budgetEur", 50_000_000), token, 200).at("/data/id").asLong();
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        Set<Long> loanedOut = new HashSet<>(squad.subList(0, 3));
        loanedOut.forEach(id -> jdbc.update("UPDATE user_player_state SET loaned_out = true WHERE career_id = ? AND base_player_id = ?", careerId, id));

        JsonNode lineup = call(post("/api/v1/fit/lineup"), Map.of("careerId", careerId, "tactic", Map.of("formation", "4-4-2")), token, 200).at("/data/slots");
        List<Long> starters = new ArrayList<>();
        lineup.forEach(s -> {
            if (s.hasNonNull("playerId")) {
                starters.add(s.get("playerId").asLong());
            }
        });
        assertThat(starters).isNotEmpty().doesNotContainAnyElementsOf(loanedOut);

        JsonNode targets = call(post("/api/v1/recommend/transfer-targets"), Map.of("careerId", careerId, "perPosition", 4,
                "slots", List.of(Map.of("position", "ST", "roleId", "poacher_attack"))), token, 200).at("/data/positions/0/targets");
        targets.forEach(t -> assertThat(squad).as("sahip olunan oyuncu aday olamaz").doesNotContain(t.at("/player/id").asLong()));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var result = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getContentAsString());
    }
}
