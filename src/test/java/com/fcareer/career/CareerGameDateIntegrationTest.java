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

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** BE-BUG-18: kariyerin oyun tarihi varken yaş gerçek tarihe değil oyun tarihine göre hesaplanır (wonderkid aramaları ikinci sezondan sonra bozulmaz). */
@IntegrationTest
class CareerGameDateIntegrationTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2031, 7, 1);

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
    void ageFollowsTheCareersGameDateInResultsAndFilters() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "gd-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", clubRepository.findAll().get(0).getId(), "budgetEur", 1_000_000), token, 200).at("/data/id").asLong();
        long playerId = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0/player/id").asLong();
        LocalDate dob = jdbc.queryForObject("SELECT dob FROM base_player WHERE id = ?", LocalDate.class, playerId);
        int realAge = Period.between(dob, LocalDate.now()).getYears();
        int gameAge = Period.between(dob, GAME_DATE).getYears();
        assertThat(gameAge).as("test verisinde oyun yaşı gerçek yaştan farklı olmalı").isNotEqualTo(realAge);

        JsonNode patched = call(patch("/api/v1/careers/" + careerId), Map.of("gameDate", GAME_DATE.toString()), token, 200);
        assertThat(patched.at("/data/gameDate").asText()).isEqualTo(GAME_DATE.toString());

        long clubId = clubRepository.findAll().get(0).getId();
        String base = "/api/v1/players?career=" + careerId + "&club=" + clubId + "&size=100";
        JsonNode items = call(get(base), null, token, 200).at("/data/items");
        JsonNode row = null;
        for (JsonNode item : items) {
            if (item.at("/id").asLong() == playerId) {
                row = item;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.at("/age").asInt()).isEqualTo(gameAge);

        String filter = base + "&age_max=";
        assertThat(ids(call(get(filter + gameAge), null, token, 200))).contains(playerId);
        assertThat(ids(call(get(filter + (gameAge - 1)), null, token, 200))).doesNotContain(playerId);
    }

    private static java.util.List<Long> ids(JsonNode body) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        body.at("/data/items").forEach(i -> ids.add(i.at("/id").asLong()));
        return ids;
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
