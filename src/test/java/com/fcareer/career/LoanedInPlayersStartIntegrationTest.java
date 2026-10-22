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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * KARIYER-UX FAZ 1 düzeltme 9: career_player.available (= loan_from_team_id IS NULL) yalnız pazar/scouting filtresidir. Bize kiralık gelen oyuncu
 * (available=false) ilk 11'e girer ve ana taktik dizilişinden düşmez; dizilişte müsaitlik kadro listesidir (squadPlayerIds).
 */
@IntegrationTest
class LoanedInPlayersStartIntegrationTest {

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

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    @Test
    void loanedInPlayersAreUnavailableForTheMarketButStillStart() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        careerPlayerWriter.syncCareer(careerId);
        String ids = String.join(",", squad.stream().map(String::valueOf).toList());
        jdbc.update("UPDATE career_player SET loan_from_team_id = 1 WHERE career_id = ? AND player_id IN (" + ids + ")", careerId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ? AND NOT available", Integer.class, careerId))
                .as("kurulum: tüm kadro bize kiralık (available=false)").isEqualTo(squad.size());

        JsonNode lineup = call(post("/api/v1/fit/lineup"), Map.of("careerId", careerId, "tactic", Map.of("formation", "4-4-2")), token);
        List<Long> starters = new ArrayList<>();
        lineup.at("/slots").forEach(s -> {
            if (s.hasNonNull("playerId")) {
                starters.add(s.get("playerId").asLong());
            }
        });
        assertThat(starters).as("available=false ilk 11'i boşaltmaz").hasSize(11);
        assertThat(new HashSet<>(squad)).containsAll(starters);

        Map<String, Object> tactic = Map.of("formation", "4-4-2", "slots", List.of());
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "name", "A", "tactic", tactic, "lineup", Map.of("GK", squad.get(0), "LB", squad.get(1))), token);
        call(put("/api/v1/user-tactics/1/main?careerId=" + careerId), null, token);
        JsonNode main = call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token);
        Set<String> kept = new HashSet<>();
        main.at("/lineup").fieldNames().forEachRemaining(kept::add);
        assertThat(kept).as("ana taktik dizilişi available'a bakmaz").containsExactlyInAnyOrder("GK", "LB");
        assertThat(main.at("/lineupDropped").asInt()).isZero();
    }
}
