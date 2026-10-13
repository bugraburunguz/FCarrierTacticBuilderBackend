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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Elle testte bulundu: bir kariyerin ürettiği (owner_career_id) oyuncu aynı kulüpte yeni açılan başka kariyerin kadrosuna sızıp /fit/lineup'ı 404 yapıyordu. */
@IntegrationTest
class CareerSquadIsolationIntegrationTest {

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

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "iso-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    @Test
    void anotherCareersGeneratedPlayersNeverJoinANewCareersSquad() throws Exception {
        int clubId = clubRepository.findAll().get(0).getId();
        String owner = register();
        long ownerCareer = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 1_000_000), owner, 200).at("/data/id").asLong();
        long generated = jdbc.queryForObject("INSERT INTO base_player (full_name, gender, owner_career_id) VALUES ('Generated Youth', 0, ?) RETURNING id", Long.class, ownerCareer);
        jdbc.update("INSERT INTO player_rating (base_player_id, game_version_id, club_id, overall, positions) VALUES (?, 27, ?, 61, ARRAY['ST'])", generated, clubId);

        String other = register();
        long otherCareer = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 1_000_000), other, 200).at("/data/id").asLong();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ? AND base_player_id = ?", Integer.class, otherCareer, generated)).isZero();
        call(post("/api/v1/fit/lineup"), Map.of("careerId", otherCareer, "tactic", Map.of("formation", "4-4-2")), other, 200);
        JsonNode squad = call(get("/api/v1/careers/" + otherCareer + "/squad"), null, other, 200).at("/data");
        assertThat(squad.size()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ?", Integer.class, otherCareer));
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
