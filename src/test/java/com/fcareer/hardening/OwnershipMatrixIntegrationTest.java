package com.fcareer.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REVIEW (testler, güvenlik): kariyer kimliği taşıyan her uç başkasının kariyerinde 404 döner (varlığı da sızmaz). Aynı uç sahibinde 404
 * dönmez: yol gerçekten var ve 404 sahiplikten geliyor (yanlış yazılmış bir yol testi boşa geçirmesin).
 */
@IntegrationTest
class OwnershipMatrixIntegrationTest {

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

    private record Call(HttpMethod method, String path, Object body) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void everyCareerScopedEndpointHidesAnotherUsersCareer() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String owner = support.register();
        // Kulüp açıkça (en kalabalık kadro) seçilir: "ilk kulüp" sırasızdır ve başka testler kulüp satırlarını güncelleyince kadrosuz kalabilir.
        // Yalnız katalog oyuncuları sayılır: içe aktarmanın ürettiği (owner_career_id) oyuncular yeni kariyerin kadrosuna girmez.
        Integer clubId = jdbc.queryForObject("SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.game_version_id = 27 "
                + "AND b.owner_career_id IS NULL GROUP BY r.club_id ORDER BY count(*) DESC, r.club_id LIMIT 1", Integer.class);
        long c = support.json(MockMvcRequestBuilders.post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("clubId", clubId, "budgetEur", 5_000_000))), owner).at("/data/id").asLong();
        String stranger = support.register();
        long squadPlayer = jdbc.queryForObject("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY base_player_id LIMIT 1", Long.class, c);
        long outsider = jdbc.queryForObject("SELECT r.base_player_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE b.owner_career_id IS NULL "
                + "AND r.base_player_id NOT IN (SELECT base_player_id FROM user_player_state WHERE career_id = ?) ORDER BY r.base_player_id LIMIT 1", Long.class, c);
        long event = support.json(MockMvcRequestBuilders.post("/api/v1/careers/" + c + "/events").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("type", "BUY", "playerId", outsider, "feeEur", 1))), owner).at("/data/id").asLong();
        Map<String, Object> tactic = Map.of("formation", "4-4-2");

        List<Call> calls = List.of(
                new Call(HttpMethod.GET, "/api/v1/careers/" + c, null),
                new Call(HttpMethod.PATCH, "/api/v1/careers/" + c, Map.of("budgetEur", 1_000_000)),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/squad", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/academy", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/events", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/moves", null),
                new Call(HttpMethod.POST, "/api/v1/careers/" + c + "/events", Map.of("type", "PROMOTE", "playerId", outsider)),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/teams", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/snapshots", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/snapshots/diff", null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/players/" + squadPlayer + "/history", null),
                new Call(HttpMethod.PUT, "/api/v1/careers/" + c + "/players/ratings", Map.of("updates", List.of(Map.of("playerId", squadPlayer, "overall", 70)))),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/tags", null),
                new Call(HttpMethod.PUT, "/api/v1/careers/" + c + "/tags/" + squadPlayer, Map.of("tag", "LOCKED")),
                new Call(HttpMethod.DELETE, "/api/v1/careers/" + c + "/tags/" + squadPlayer, null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/shortlist", null),
                new Call(HttpMethod.PUT, "/api/v1/careers/" + c + "/shortlist/" + outsider, Map.of()),
                new Call(HttpMethod.DELETE, "/api/v1/careers/" + c + "/shortlist/" + outsider, null),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/seasons", null),
                new Call(HttpMethod.PUT, "/api/v1/careers/" + c + "/seasons/2027", Map.of("leaguePosition", 1)),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/backup", null),
                new Call(HttpMethod.POST, "/api/v1/careers/" + c + "/backup", Map.of("version", 1)),
                new Call(HttpMethod.GET, "/api/v1/careers/" + c + "/starter-tactic", null),
                new Call(HttpMethod.POST, "/api/v1/careers/" + c + "/recommendations", Map.of("position", "ST")),
                new Call(HttpMethod.POST, "/api/v1/scouting/" + c + "/search", Map.of("position", "ST", "roleId", "poacher_attack")),
                new Call(HttpMethod.GET, "/api/v1/advisor/positions/" + c, null),
                new Call(HttpMethod.POST, "/api/v1/careers/" + c + "/development/recommendations", Map.of()),
                new Call(HttpMethod.POST, "/api/v1/fit/lineup", Map.of("careerId", c, "tactic", tactic)),
                new Call(HttpMethod.POST, "/api/v1/fit/squad", Map.of("careerId", c, "tactic", tactic)),
                new Call(HttpMethod.GET, "/api/v1/user-tactics?careerId=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/user-tactics/main?careerId=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/players?career=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/players/facets?career=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/players/" + squadPlayer + "?career=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/players/" + squadPlayer + "/roles?career=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/players/" + squadPlayer + "/contract?career=" + c, null),
                new Call(HttpMethod.GET, "/api/v1/wonderkids?career=" + c, null),
                new Call(HttpMethod.DELETE, "/api/v1/careers/" + c + "/events/" + event, null),
                new Call(HttpMethod.DELETE, "/api/v1/careers/" + c, null));

        List<String> leaks = new ArrayList<>();
        for (Call call : calls) {
            int status = status(call, stranger);
            if (status != 404) {
                leaks.add(call + " → " + status);
            }
        }
        assertThat(leaks).as("yabancı 404 almalı").isEmpty();

        List<String> missingRoutes = new ArrayList<>();
        for (Call call : calls) {
            int status = status(call, owner);
            if (status == 404) {
                missingRoutes.add(call.toString());
            }
        }
        assertThat(missingRoutes).as("sahibi 404 almamalı (yol var, 404 sahiplikten)").isEmpty();
    }

    private int status(Call call, String token) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(call.method(), call.path()).header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON);
        if (call.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(call.body()));
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }
}
