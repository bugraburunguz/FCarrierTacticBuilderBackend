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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@IntegrationTest
class CareerDeskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    void manualRatingUpdatesBuildSnapshotsDiffAndHistory() throws Exception {
        String token = register();
        long careerId = createCareer(token);
        JsonNode squad = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data");
        long playerId = squad.get(0).at("/player/id").asLong();
        int overall = squad.get(0).at("/player/overall").asInt();

        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", overall, "potential", overall + 4))), token, 200);
        JsonNode second = call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", overall + 2, "potential", overall + 4)), "note", "sezon sonu"), token, 200);
        assertThat(second.at("/data/source").asText()).isEqualTo("MANUAL");

        assertThat(call(get("/api/v1/careers/" + careerId + "/snapshots"), null, token, 200).at("/data")).hasSize(2);
        JsonNode diff = call(get("/api/v1/careers/" + careerId + "/snapshots/diff"), null, token, 200).at("/data");
        assertThat(diff.at("/changed").asInt()).isEqualTo(1);
        assertThat(diff.at("/rows/0/playerId").asLong()).isEqualTo(playerId);
        assertThat(diff.at("/rows/0/overallFrom").asInt()).isEqualTo(overall);
        assertThat(diff.at("/rows/0/overallTo").asInt()).isEqualTo(overall + 2);

        JsonNode history = call(get("/api/v1/careers/" + careerId + "/players/" + playerId + "/history"), null, token, 200).at("/data");
        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("overall").asInt()).isEqualTo(overall + 2);

        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", 999_999_999L, "overall", 70))), token, 422);
    }

    @Test
    void spoilerModeHidesExactPotentialOnServerSide() throws Exception {
        String token = register();
        long careerId = createCareer(token);
        JsonNode squad = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data");
        long playerId = squad.get(0).at("/player/id").asLong();
        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", 80, "potential", 83))), token, 200);

        JsonNode open = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0");
        assertThat(open.at("/player/potential").asInt()).isEqualTo(83);

        call(patch("/api/v1/careers/" + careerId), Map.of("spoilerMode", "SCOUT_RANGE"), token, 200);
        JsonNode hidden = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0");
        assertThat(hidden.at("/player/potential").isMissingNode()).isTrue();
        assertThat(hidden.at("/player/potentialRange").asText()).isEqualTo("80–84");
        assertThat(hidden.at("/dynamicPotential").isMissingNode()).isTrue();

        JsonNode search = call(get("/api/v1/players?size=5&career=" + careerId), null, token, 200).at("/data/items");
        search.forEach(p -> assertThat(p.at("/potential").isMissingNode()).isTrue());
        call(patch("/api/v1/careers/" + careerId), Map.of("spoilerMode", "BOZUK"), token, 400);
    }

    @Test
    void tagsShortlistSeasonsAndBackupRoundTrip() throws Exception {
        String token = register();
        long careerId = createCareer(token);
        long playerId = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0/player/id").asLong();
        long target = basePlayerRepository.findAll().stream().filter(p -> p.getId() != playerId).findFirst().orElseThrow().getId();

        call(put("/api/v1/careers/" + careerId + "/tags/" + playerId), Map.of("tag", "FOR_SALE"), token, 200);
        call(put("/api/v1/careers/" + careerId + "/tags/" + playerId), Map.of("tag", "LOCKED"), token, 200);
        assertThat(call(get("/api/v1/careers/" + careerId + "/tags"), null, token, 200).at("/data/0/tag").asText()).isEqualTo("LOCKED");
        call(put("/api/v1/careers/" + careerId + "/tags/" + playerId), Map.of("tag", "BOZUK"), token, 400);

        call(put("/api/v1/careers/" + careerId + "/shortlist/" + target), Map.of("note", "sol bek", "maxFeeEur", 8_000_000), token, 200);
        JsonNode listed = call(get("/api/v1/careers/" + careerId + "/shortlist"), null, token, 200).at("/data/0");
        assertThat(listed.at("/maxFeeEur").asLong()).isEqualTo(8_000_000L);
        assertThat(listed.at("/player/id").asLong()).isEqualTo(target);
        assertThat(listed.at("/player/name").asText()).isNotBlank();
        assertThat(listed.at("/player/overall").asInt()).isPositive();
        call(put("/api/v1/careers/" + careerId + "/seasons/2027"), Map.of("leaguePosition", 2, "trophies", List.of("Kupa"), "note", "iyi sezon"), token, 200);

        JsonNode backup = call(get("/api/v1/careers/" + careerId + "/backup"), null, token, 200).at("/data");
        long other = createCareer(token);
        call(post("/api/v1/careers/" + other + "/backup"), backup, token, 200);

        assertThat(call(get("/api/v1/careers/" + other + "/shortlist"), null, token, 200).at("/data")).hasSize(1);
        assertThat(call(get("/api/v1/careers/" + other + "/seasons"), null, token, 200).at("/data/0/trophies/0").asText()).isEqualTo("Kupa");

        call(delete("/api/v1/careers/" + careerId + "/tags/" + playerId), null, token, 200);
        assertThat(call(get("/api/v1/careers/" + careerId + "/tags"), null, token, 200).at("/data")).isEmpty();
        call(delete("/api/v1/careers/" + careerId + "/shortlist/" + target), null, token, 200);
    }

    @Test
    void invalidBackupsAreRejectedWith400NotA500() throws Exception {
        String token = register();
        long careerId = createCareer(token);
        String url = "/api/v1/careers/" + careerId + "/backup";
        long playerId = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0/player/id").asLong();

        call(post(url), backup(Map.of("tags", List.of(Map.of("playerId", playerId, "tag", "X")))), token, 400);
        call(post(url), backup(Map.of("spoilerMode", "BOZUK")), token, 400);
        call(post(url), backup(Map.of("shortlist", List.of(Map.of("playerId", playerId, "maxFeeEur", -1)))), token, 400);
        call(post(url), backup(Map.of("seasons", List.of(Map.of("season", 2027, "leaguePosition", -3)))), token, 400);
        call(post(url), backup(Map.of("tags", java.util.Collections.nCopies(5_000, Map.of("playerId", playerId, "tag", "LOCKED")))), token, 400);
        call(post(url), backup(Map.of("version", 7)), token, 422);
        call(post(url), backup(Map.of("tags", List.of(Map.of("playerId", playerId, "tag", "LOCKED")))), token, 200);
    }

    private static Map<String, Object> backup(Map<String, Object> overrides) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("version", 1));
        body.putAll(overrides);
        return body;
    }

    @Test
    void otherUsersCannotTouchTheCareer() throws Exception {
        long careerId = createCareer(register());
        String stranger = register();
        call(get("/api/v1/careers/" + careerId + "/snapshots"), null, stranger, 404);
        call(put("/api/v1/careers/" + careerId + "/tags/1"), Map.of("tag", "LOCKED"), stranger, 404);
        call(get("/api/v1/careers/" + careerId + "/backup"), null, stranger, 404);
    }

    private long createCareer(String token) throws Exception {
        long clubId = clubRepository.findAll().get(0).getId();
        return call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 5_000_000), token, 200).at("/data/id").asLong();
    }

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "desk-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
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
