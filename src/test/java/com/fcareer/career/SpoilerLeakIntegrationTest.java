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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@IntegrationTest
class SpoilerLeakIntegrationTest {

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

    private String token;
    private long careerId;
    private long playerId;

    @BeforeEach
    void ranged() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        token = call(post("/api/v1/auth/register"), Map.of("email", "spoil-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        long clubId = clubRepository.findAll().get(0).getId();
        careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 50_000_000), token, 200).at("/data/id").asLong();
        playerId = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0/player/id").asLong();
        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", 80, "potential", 83))), token, 200);
        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", 82, "potential", 86))), token, 200);
        call(patch("/api/v1/careers/" + careerId), Map.of("spoilerMode", "SCOUT_RANGE"), token, 200);
    }

    @Test
    void snapshotDiffAndHistoryOnlyShowBands() throws Exception {
        JsonNode row = call(get("/api/v1/careers/" + careerId + "/snapshots/diff"), null, token, 200).at("/data/rows/0");
        assertThat(row.at("/potentialFrom").asInt()).isEqualTo(80);
        assertThat(row.at("/potentialTo").asInt()).isEqualTo(85);

        JsonNode history = call(get("/api/v1/careers/" + careerId + "/players/" + playerId + "/history"), null, token, 200).at("/data");
        history.forEach(p -> assertThat(p.at("/potential").asInt() % 5).isZero());
    }

    @Test
    void potentialFiltersAreRoundedToBandsSoBinarySearchCannotFindTheExactValue() throws Exception {
        String base = "/api/v1/players?career=" + careerId + "&size=50&sort=overall&gender=0";

        JsonNode low = call(get(base + "&pot_min=81"), null, token, 200).at("/data");
        JsonNode high = call(get(base + "&pot_min=83"), null, token, 200).at("/data");
        assertThat(high.at("/total").asLong()).isEqualTo(low.at("/total").asLong());

        JsonNode maxA = call(get(base + "&pot_max=82"), null, token, 200).at("/data");
        JsonNode maxB = call(get(base + "&pot_max=84"), null, token, 200).at("/data");
        assertThat(maxB.at("/total").asLong()).isEqualTo(maxA.at("/total").asLong());
    }

    @Test
    void recommendationScoutingTeamAndSimilarResponsesCarryNoExactPotential() throws Exception {
        JsonNode targets = call(post("/api/v1/recommend/transfer-targets"), Map.of("careerId", careerId, "perPosition", 3,
                "slots", List.of(Map.of("position", "ST", "roleId", "poacher_attack"))), token, 200);
        assertNoExactPotential(targets.at("/data"));

        JsonNode view = call(post("/api/v1/recommend/slot-view"), Map.of("careerId", careerId, "scope", "MARKET",
                "slot", Map.of("roleId", "winger_attack", "position", "RW")), token, 200);
        assertNoExactPotential(view.at("/data"));

        JsonNode scouting = call(post("/api/v1/scouting/" + careerId + "/search"), Map.of("position", "ST", "roleId", "poacher_attack", "potentialMin", 83), token, 200);
        assertNoExactPotential(scouting.at("/data"));

        JsonNode similar = call(get("/api/v1/players/" + playerId + "/similar?career=" + careerId), null, token, 200);
        assertNoExactPotential(similar.at("/data"));
    }

    private static void assertNoExactPotential(JsonNode node) {
        if (node.isObject()) {
            assertThat(node.has("potential") && !node.get("potential").isNull()).as("potential leaked in " + node).isFalse();
            node.fields().forEachRemaining(e -> assertNoExactPotential(e.getValue()));
        } else if (node.isArray()) {
            node.forEach(SpoilerLeakIntegrationTest::assertNoExactPotential);
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String bearer, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getContentAsString());
    }
}
