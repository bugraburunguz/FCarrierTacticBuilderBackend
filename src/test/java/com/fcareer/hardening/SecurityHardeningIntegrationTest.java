package com.fcareer.hardening;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@IntegrationTest
class SecurityHardeningIntegrationTest {

    private static final String PASSWORD = "Sup3rSecret!";

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
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCatalog() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void secondSubscriptionWhileActiveIsRejected() throws Exception {
        String token = register();
        call(post("/api/v1/billing/subscribe"), null, token, 200);
        JsonNode again = call(post("/api/v1/billing/subscribe"), null, token, 409);
        assertThat(again.at("/error/0/code").asText()).isEqualTo("fc.exception.0019");
    }

    @Test
    void developmentWithForeignCareerIsForbidden() throws Exception {
        String owner = register();
        String stranger = register();
        long careerId = createCareer(owner);
        long playerId = call(get("/api/v1/players?size=1"), null, null, 200).at("/data/items/0/id").asLong();

        call(post("/api/v1/players/" + playerId + "/development"), Map.of("careerId", careerId), stranger, 403);
        call(post("/api/v1/players/" + playerId + "/development"), Map.of("careerId", careerId), null, 403);
        call(post("/api/v1/players/" + playerId + "/development"), Map.of("careerId", careerId), owner, 200);
        call(post("/api/v1/players/" + playerId + "/development"), Map.of(), null, 200);
    }

    @Test
    void careerGeneratedPlayerIsHiddenFromOthers() throws Exception {
        String owner = register();
        long careerId = createCareer(owner);
        long playerId = call(get("/api/v1/players?size=1"), null, null, 200).at("/data/items/0/id").asLong();
        jdbc.update("UPDATE base_player SET owner_career_id = ? WHERE id = ?", careerId, playerId);
        try {
            call(get("/api/v1/players/" + playerId), null, null, 404);
            call(get("/api/v1/players/" + playerId), null, register(), 404);
            call(post("/api/v1/players/" + playerId + "/development"), Map.of(), null, 404);
            call(get("/api/v1/players/" + playerId + "?career=" + careerId), null, owner, 200);
        } finally {
            jdbc.update("UPDATE base_player SET owner_career_id = NULL WHERE id = ?", playerId);
        }
    }

    @Test
    void slotViewIsCappedAndHasNoPaidInsights() throws Exception {
        String token = register();
        long careerId = createCareer(token);
        JsonNode view = call(post("/api/v1/recommend/slot-view"), Map.of("careerId", careerId, "scope", "MARKET",
                "slot", Map.of("roleId", "winger_attack", "position", "RW")), token, 200);

        assertThat(view.at("/data/items").size()).isLessThanOrEqualTo(3);
        assertThat(view.at("/data/scoutQuery").isMissingNode()).isTrue();
        view.at("/data/items").forEach(i -> {
            assertThat(i.at("/roleFit/reasons").isMissingNode()).isTrue();
            assertThat(i.at("/roleFit/weaponChecks").isMissingNode()).isTrue();
            assertThat(i.at("/intentFit").isMissingNode()).isTrue();
        });
    }

    @Test
    void anonymousPreviewIsCappedAndHasNoPaidInsights() throws Exception {
        JsonNode preview = call(post("/api/v1/recommend/preview"), Map.of("roleId", "winger_attack", "position", "RW", "limit", 8,
                "setup", Map.of("buildUp", "Short", "depth", 90)), null, 200);

        assertThat(preview.at("/data/items").size()).isBetween(1, 3);
        assertThat(preview.at("/data/scoutQuery").isMissingNode()).isTrue();
        preview.at("/data/items").forEach(i -> {
            assertThat(i.at("/roleFit/reasons").isMissingNode()).isTrue();
            assertThat(i.at("/roleFit/weaponChecks").isMissingNode()).isTrue();
            assertThat(i.at("/intentFit").isMissingNode()).isTrue();
            assertThat(i.at("/setupDelta").isMissingNode()).isTrue();
        });
    }

    @Test
    void votesUpsertChangeAndRevoke() throws Exception {
        String token = register();
        JsonNode up = call(put("/api/v1/ut/sbc/votes/36"), Map.of("vote", 1), token, 200);
        assertThat(up.at("/data/up").asInt()).isEqualTo(1);
        assertThat(up.at("/data/mine").asInt()).isEqualTo(1);

        JsonNode flipped = call(put("/api/v1/ut/sbc/votes/36"), Map.of("vote", -1), token, 200);
        assertThat(flipped.at("/data/up").asInt()).isZero();
        assertThat(flipped.at("/data/down").asInt()).isEqualTo(1);

        JsonNode revoked = call(put("/api/v1/ut/sbc/votes/36"), Map.of("vote", 0), token, 200);
        assertThat(revoked.at("/data/mine").asInt()).isZero();
        assertThat(revoked.at("/data/down").asInt()).isZero();

        call(put("/api/v1/ut/sbc/votes/0"), Map.of("vote", 1), token, 400);
        call(put("/api/v1/ut/sbc/votes/36"), Map.of("vote", 5), token, 400);
        call(put("/api/v1/ut/sbc/votes/36"), Map.of("vote", 1), null, 401);
    }

    @Test
    void oversizedCaptureBodyIsRejected() throws Exception {
        String token = register();
        String huge = "x".repeat(8_000_001);
        call(post("/api/v1/ut/capture"), Map.of("captures", new Object[]{Map.of("url", "https://x/y", "ts", 1, "body", huge)}), token, 400);
    }

    private long createCareer(String token) throws Exception {
        long clubId = clubRepository.findAll().get(0).getId();
        return call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 1_000_000), token, 200).at("/data/id").asLong();
    }

    private String register() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"),
                Map.of("email", "user-" + UUID.randomUUID() + "@example.com", "password", PASSWORD), null, 200);
        return registered.at("/data/accessToken").asText();
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
