package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@IntegrationTest
class KnowledgeBaseIntegrationTest {

    private static final String UT = "https://utas.mob.v1.prd.futc-ext.gcp.ea.com/ut/game/fc27";
    private static final String ADMIN_KEY = "X-Admin-Key";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM contrib_queue");
        jdbc.update("DELETE FROM contrib_event");
        jdbc.update("DELETE FROM sbc_challenge");
        jdbc.update("DELETE FROM sbc_set");
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private static String hub(String name) {
        return "{\"categories\":[{\"categoryId\":1,\"name\":\"Special\",\"sets\":[{\"setId\":9036,\"name\":\"" + name
                + "\",\"challengesCount\":1,\"challengesCompletedCount\":0,\"repeatable\":false,\"endTime\":86400,\"awards\":[{}]}]}]}";
    }

    private static String challenge(int rating) {
        return "{\"challenges\":[{\"name\":\"Como v Roma\",\"status\":\"NOT_STARTED\",\"setId\":9036,\"challengeId\":90367,\"repeatable\":false,\"formation\":\"f451\",\"timesCompleted\":0,"
                + "\"elgReq\":[{\"type\":\"TEAM_RATING_1_TO_100\",\"eligibilitySlot\":1,\"eligibilityKey\":19,\"eligibilityValue\":" + rating
                + "},{\"type\":\"SCOPE\",\"eligibilitySlot\":1,\"eligibilityKey\":13,\"eligibilityValue\":0}],\"awards\":[],\"type\":\"OPEN_CHALLENGE\"}]}";
    }

    private Object[] captures(String hubBody, String challengeBody) {
        return new Object[]{
                Map.of("url", UT + "/sbs/hub/v2", "ts", 1, "body", hubBody),
                Map.of("url", UT + "/sbs/setid/9036/challenges", "ts", 2, "body", challengeBody),
                Map.of("url", UT + "/club", "ts", 3, "body", "{\"itemData\":[{\"id\":1,\"assetId\":5,\"rating\":80,\"preferredPosition\":\"ST\"}]}")};
    }

    @Test
    void contributionsAreConfirmedQueuedAndResolvedByConsensus() throws Exception {
        long first = register();
        long second = register();
        long outsider = register();
        admin("/api/v1/admin/ut/contributors/" + first + "/grant");
        admin("/api/v1/admin/ut/contributors/" + second + "/grant");
        String token1 = login(first);
        String token2 = login(second);

        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(75))), login(outsider), 402);

        JsonNode created = call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(75))), token1, 200);
        assertThat(created.at("/data/created").asInt()).isEqualTo(2);

        JsonNode list = call(get("/api/v1/ut/kb/sbc?challenges=true"), null, null, 200);
        assertThat(list.at("/data").toString()).contains("Marquee").contains("Como v Roma");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sbc_set WHERE content_hash = 'stub'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ut_item WHERE source = 'CONTRIB'", Integer.class)).isZero();

        JsonNode same = call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(75))), token2, 200);
        assertThat(same.at("/data/unchanged").asInt()).isEqualTo(2);

        JsonNode conflict = call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(80))), token1, 200);
        assertThat(conflict.at("/data/queued").asInt()).isEqualTo(1);
        assertThat(call(get("/api/v1/admin/ut/queue").header(ADMIN_KEY, "test-key"), null, null, 200).at("/data")).hasSize(1);

        JsonNode agreed = call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(80))), token2, 200);
        assertThat(agreed.at("/data/updated").asInt()).isEqualTo(1);
        JsonNode detail = call(get("/api/v1/ut/kb/sbc/9036"), null, null, 200);
        assertThat(detail.at("/data/challenges/0/requirements").toString()).contains("80");
    }

    @Test
    void adminResolvesQueue() throws Exception {
        long contributor = register();
        admin("/api/v1/admin/ut/contributors/" + contributor + "/grant");
        String token = login(contributor);
        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(75))), token, 200);
        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", captures(hub("Marquee"), challenge(81))), token, 200);

        JsonNode queue = call(get("/api/v1/admin/ut/queue").header(ADMIN_KEY, "test-key"), null, null, 200).at("/data");
        long queueId = queue.get(0).get("id").asLong();
        String candidate = queue.get(0).at("/candidates/1/hash").asText();
        call(post("/api/v1/admin/ut/queue/" + queueId + "/resolve").header(ADMIN_KEY, "test-key"), Map.of("hash", candidate), null, 200);

        assertThat(call(get("/api/v1/ut/kb/sbc/9036"), null, null, 200).at("/data/challenges/0/requirements").toString()).contains("81");
        assertThat(call(get("/api/v1/admin/ut/queue").header(ADMIN_KEY, "test-key"), null, null, 200).at("/data")).isEmpty();
    }

    @Test
    void linkTokenWorksOnlyForCaptureEndpoints() throws Exception {
        long contributor = register();
        admin("/api/v1/admin/ut/contributors/" + contributor + "/grant");
        String jwt = login(contributor);

        JsonNode link = call(post("/api/v1/ut/capture/link?mode=CONTRIB"), null, jwt, 200);
        String linkToken = link.at("/data/token").asText();
        assertThat(link.at("/data/mode").asText()).isEqualTo("CONTRIB");

        call(post("/api/v1/ut/contrib/capture").header("X-Link-Token", linkToken), Map.of("captures", captures(hub("Link"), challenge(70))), null, 200);
        call(post("/api/v1/ut/capture").header("X-Link-Token", linkToken), Map.of("captures", new Object[]{}), null, 200);
        call(get("/api/v1/me").header("X-Link-Token", linkToken), null, null, 401);

        long plain = register();
        JsonNode personal = call(post("/api/v1/ut/capture/link?mode=CONTRIB"), null, login(plain), 200);
        assertThat(personal.at("/data/mode").asText()).isEqualTo("PERSONAL");
        call(post("/api/v1/ut/capture/link"), null, null, 401);
    }

    @Test
    void publicRulesItemsAndPriceAggregation() throws Exception {
        assertThat(call(get("/api/v1/ut/rules"), null, null, 200).at("/data/squadRating/size").asInt()).isEqualTo(11);

        call(post("/api/v1/admin/ut/items/sync-base").header(ADMIN_KEY, "test-key"), null, null, 200);
        JsonNode items = call(get("/api/v1/ut/kb/items?limit=3"), null, null, 200).at("/data");
        assertThat(items.size()).isEqualTo(3);
        long itemId = items.get(0).get("id").asLong();
        assertThat(call(get("/api/v1/ut/kb/items/" + itemId), null, null, 200).at("/data/versions")).isNotEmpty();

        long contributor = register();
        admin("/api/v1/admin/ut/contributors/" + contributor + "/grant");
        long contributorId = jdbc.queryForObject("SELECT id FROM contributor WHERE user_id = ?", Long.class, contributor);
        for (int value : List.of(1000, 1100, 1200, 90000)) {
            jdbc.update("INSERT INTO price_obs (ut_item_id, platform, observed_at, kind, value, contributor_id) VALUES (?, 'CONSOLE', now(), 'BIN', ?, ?)", itemId, value, contributorId);
        }
        call(post("/api/v1/admin/ut/prices/aggregate").header(ADMIN_KEY, "test-key"), null, null, 200);
        JsonNode prices = call(get("/api/v1/ut/kb/items/" + itemId + "/prices?platform=CONSOLE&range=24h"), null, null, 200).at("/data");
        assertThat(prices).isNotEmpty();
        assertThat(prices.get(0).get("p50").asInt()).isBetween(1000, 1200);
        call(get("/api/v1/ut/kb/items/" + itemId + "/prices?platform=XBOX"), null, null, 400);
    }

    @Test
    void adminCanEnterSetsByHand() throws Exception {
        JsonNode body = objectMapper.readTree("{\"name\":\"Elle\",\"category\":\"Upgrades\",\"repeatable\":true,\"reward\":[{\"type\":\"pack\",\"name\":\"Gold\"}]}");
        call(put("/api/v1/admin/ut/sbc-set/9100").header(ADMIN_KEY, "test-key"), body, null, 200);
        call(put("/api/v1/admin/ut/promos").header(ADMIN_KEY, "test-key"), Map.of("code", "totw", "name", "Team of the Week"), null, 200);

        assertThat(call(get("/api/v1/ut/kb/sbc/9100"), null, null, 200).at("/data/name").asText()).isEqualTo("Elle");
        assertThat(call(get("/api/v1/ut/kb/promos"), null, null, 200).at("/data").toString()).contains("TOTW");
        call(put("/api/v1/admin/ut/unknown/1").header(ADMIN_KEY, "test-key"), body, null, 400);
    }

    @Test
    void outdatedExtensionIsAskedToUpdate() throws Exception {
        long contributor = register();
        admin("/api/v1/admin/ut/contributors/" + contributor + "/grant");
        String token = login(contributor);
        Map<String, Object> body = Map.of("captures", captures(hub("Surum"), challenge(70)));

        call(post("/api/v1/ut/contrib/capture").header("X-Ext-Version", "0.2.0"), body, token, 426);
        call(post("/api/v1/ut/contrib/capture").header("X-Ext-Version", "0.3.0"), body, token, 200);
        call(post("/api/v1/ut/contrib/capture").header("X-Ext-Version", "1.0.0"), body, token, 200);
    }

    @Test
    void captureRulesAndEaRolesArePublic() throws Exception {
        JsonNode rules = call(get("/api/v1/ut/capture/rules"), null, null, 200).at("/data");
        assertThat(rules.at("/deny").toString()).contains("sid=");
        assertThat(rules.at("/personalOnly").toString()).contains("/club");

        JsonNode roles = call(get("/api/v1/tactics/ea-roles"), null, null, 200).at("/data");
        assertThat(roles.size()).isGreaterThan(100);
        assertThat(roles.toString()).contains("\"position\":\"CAM\"");
    }

    private long register() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", "kb-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200);
        String token = registered.at("/data/accessToken").asText();
        return call(get("/api/v1/me"), null, token, 200).at("/data/id").asLong();
    }

    private String login(long userId) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, userId);
        return call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    private void admin(String path) throws Exception {
        call(post(path).header(ADMIN_KEY, "test-key"), null, null, 200);
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
