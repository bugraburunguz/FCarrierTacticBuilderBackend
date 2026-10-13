package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** ENG-04: kariyer sorgularında tüm yaşlar career.game_date'ten (fit oyuncusu, benzer oyuncular, wonderkid). */
@IntegrationTest
class GameDateAgesIntegrationTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2031, 7, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;

    private String token;
    private long careerId;

    @BeforeEach
    void seed() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        token = call(post("/api/v1/auth/register"), Map.of("email", "gda-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        careerId = call(post("/api/v1/careers"), Map.of("clubId", clubRepository.findAll().get(0).getId(), "budgetEur", 5_000_000), token, 200).at("/data/id").asLong();
        call(patch("/api/v1/careers/" + careerId), Map.of("gameDate", GAME_DATE.toString()), token, 200);
    }

    private int gameAge(long playerId) {
        LocalDate dob = jdbc.queryForObject("SELECT dob FROM base_player WHERE id = ?", LocalDate.class, playerId);
        return Period.between(dob, GAME_DATE).getYears();
    }

    @Test
    void fitPlayersCarryTheGameDateAgeInCareerMode() {
        long playerId = jdbc.queryForObject("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id LIMIT 1", Long.class, careerId);

        FitPlayer inCareer = playerService.fitPlayers(List.of(playerId), (short) 27, careerId).get(0);
        FitPlayer inCatalog = playerService.fitPlayers(List.of(playerId), (short) 27, null).get(0);

        assertThat(inCareer.getAge()).isEqualTo(gameAge(playerId));
        assertThat(inCatalog.getAge()).isLessThan(inCareer.getAge());
    }

    @Test
    void similarPlayersRespectTheCareersGameDateInTheAgeFilter() throws Exception {
        long playerId = jdbc.queryForObject("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id LIMIT 1", Long.class, careerId);

        JsonNode similar = call(get("/api/v1/players/" + playerId + "/similar?career=" + careerId + "&age_max=40&limit=50"), null, token, 200).at("/data");

        assertThat(similar).as("test verisinde benzer oyuncu olmalı").isNotEmpty();
        similar.forEach(s -> assertThat(gameAge(s.at("/player/id").asLong())).as("oyun yaşı").isLessThanOrEqualTo(40));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String bearer, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        var result = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getContentAsString());
    }
}
