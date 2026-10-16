package com.fcareer.ut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.ut.repository.EaIdRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class EaIdVotingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EaIdRepository eaIds;

    private long newUser() throws Exception {
        String body = "{\"email\":\"vote-" + UUID.randomUUID() + "@example.com\",\"password\":\"Sup3rSecret!\",\"privacyVersion\":1}";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(response).at("/data/accessToken").asText();
        String me = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(me).at("/data/id").asLong();
    }

    private void league(String name) {
        jdbc.update("DELETE FROM ea_id_suggestion WHERE name = ?", name);
        jdbc.update("DELETE FROM league WHERE name = ?", name);
        jdbc.update("INSERT INTO league (name) VALUES (?)", name);
    }

    private Integer eaLeagueId(String name) {
        return jdbc.queryForObject("SELECT ea_league_id FROM league WHERE name = ?", Integer.class, name);
    }

    @Test
    void votesFromNonContributorsAreIgnored() throws Exception {
        league("Oy Ligi A");
        for (int i = 0; i < EaIdRepository.APPLY_THRESHOLD; i++) {
            eaIds.learnLeagues(Map.of(88_001, "Oy Ligi A"), newUser());
        }

        assertThat(eaLeagueId("Oy Ligi A")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ea_id_suggestion WHERE name = 'Oy Ligi A'", Integer.class)).isZero();
    }

    @Test
    void contributorVotesCountAndApplyAtTheThreshold() throws Exception {
        league("Oy Ligi B");
        for (int i = 0; i < EaIdRepository.APPLY_THRESHOLD; i++) {
            long user = newUser();
            jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100)", user);
            eaIds.learnLeagues(Map.of(88_002, "Oy Ligi B"), user);
        }

        assertThat(eaLeagueId("Oy Ligi B")).isEqualTo(88_002);
    }
}
