package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class PriceCaptureIntegrationTest {

    private static final String UT = "https://utas.mob.v1.prd.futc-ext.gcp.ea.com/ut/game/fc27";
    private static final long ASSET = 9_000_001L;
    private static final long TOTW_RESOURCE = 50_009_001L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private long baseItem;
    private long totwItem;

    @BeforeEach
    void items() {
        cleanUp();
        short gv = jdbc.queryForObject("SELECT id FROM game_version ORDER BY id DESC LIMIT 1", Short.class);
        baseItem = insertItem(gv, ASSET, 90);
        totwItem = insertItem(gv, TOTW_RESOURCE, 91);
    }

    /** Paylaşılan context: ADMIN kaynaklı test kartı ve fiyat satırları sonraki sınıflara kalmasın. */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM price_obs");
        jdbc.update("DELETE FROM price_agg");
        jdbc.update("DELETE FROM price_capture_stat");
        jdbc.update("DELETE FROM ut_item WHERE ea_asset_id = ?", ASSET);
    }

    private long insertItem(short gv, long resourceId, int rating) {
        return jdbc.queryForObject("INSERT INTO ut_item (game_version_id, ea_asset_id, ea_resource_id, name, rating, position, source) VALUES (?, ?, ?, 'Test Oyuncu', ?, 'ST', 'ADMIN') RETURNING id",
                Long.class, gv, ASSET, resourceId, rating);
    }

    private static String auction(long resourceId, int rating, long buyNow, long expires) {
        return "{\"buyNowPrice\":" + buyNow + ",\"currentBid\":0,\"startingBid\":" + (buyNow - 500) + ",\"expires\":" + expires
                + ",\"itemData\":{\"id\":1,\"assetId\":" + ASSET + ",\"resourceId\":" + resourceId + ",\"rareflag\":3,\"rating\":" + rating
                + ",\"itemType\":\"player\",\"lastSalePrice\":4500}}";
    }

    private JsonNode contribute(String path, String... auctions) throws Exception {
        return contributeAt(Instant.now().minusSeconds(3_600).toEpochMilli(), path, auctions);
    }

    private JsonNode contributeAt(long capturedAtMillis, String path, String... auctions) throws Exception {
        long user = register();
        jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100) ON CONFLICT DO NOTHING", user);
        String body = "{\"auctionInfo\":[" + String.join(",", auctions) + "]}";
        return call(post("/api/v1/ut/contrib/capture"), Map.of("captures", new Object[]{Map.of("url", UT + path, "ts", capturedAtMillis, "body", body)}), login(user), 200);
    }

    @Test
    void promoPriceGoesToThePromoItemNotTheBaseCard() throws Exception {
        JsonNode result = contribute("/transfermarket", auction(TOTW_RESOURCE, 91, 5000, 3000), auction(77_777_777L, 91, 5000, 3000));

        assertThat(count(totwItem)).isEqualTo(1);
        assertThat(count(baseItem)).isZero();
        assertThat(result.at("/data/pricesSkipped").asInt()).isEqualTo(1);
    }

    @Test
    void personalListsAndLastSaleAreNeverMarketPrices() throws Exception {
        contribute("/tradepile", auction(TOTW_RESOURCE, 91, 5000, 3000));
        contribute("/watchlist", auction(TOTW_RESOURCE, 91, 5000, 3000));
        contribute("/trade/status/lite", auction(TOTW_RESOURCE, 91, 5000, 3000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_obs", Integer.class)).isZero();

        contribute("/transfermarket", auction(TOTW_RESOURCE, 91, 5000, 3000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_obs WHERE kind = 'LAST_SALE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_obs WHERE kind = 'BIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void expiredAuctionsAndCaptureTimeAreRespected() throws Exception {
        contribute("/transfermarket", auction(TOTW_RESOURCE, 91, 5000, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_obs", Integer.class)).isZero();

        long captured = Instant.now().minusSeconds(7_200).toEpochMilli();
        contributeAt(captured, "/transfermarket", auction(TOTW_RESOURCE, 91, 5000, 3000));
        long observed = jdbc.queryForObject("SELECT extract(epoch FROM observed_at)::bigint FROM price_obs", Long.class);
        assertThat(observed).isCloseTo(captured / 1000, org.assertj.core.api.Assertions.within(1L));
    }

    @Test
    void singleContributorMedianIsNotDraggedToTheMinimum() throws Exception {
        long contributor = contributorId();
        for (int value : new int[]{1000, 1100, 1200}) {
            observation(baseItem, value, contributor);
        }
        aggregate();

        assertThat(jdbc.queryForObject("SELECT p50 FROM price_agg WHERE ut_item_id = ?", Integer.class, baseItem)).isEqualTo(1100);
        assertThat(jdbc.queryForObject("SELECT low_confidence FROM price_agg WHERE ut_item_id = ?", Boolean.class, baseItem)).isTrue();
    }

    @Test
    void aProlificContributorDoesNotDominateTheBucket() throws Exception {
        long heavy = contributorId();
        long light = contributorId();
        for (int i = 0; i < 10; i++) {
            observation(baseItem, 1000, heavy);
        }
        observation(baseItem, 2000, light);
        aggregate();

        assertThat(jdbc.queryForObject("SELECT p50 FROM price_agg WHERE ut_item_id = ?", Integer.class, baseItem)).isBetween(1001, 1999);
        assertThat(jdbc.queryForObject("SELECT low_confidence FROM price_agg WHERE ut_item_id = ?", Boolean.class, baseItem)).isFalse();
    }

    private long contributorId() throws Exception {
        long user = register();
        jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100)", user);
        return jdbc.queryForObject("SELECT id FROM contributor WHERE user_id = ?", Long.class, user);
    }

    private void observation(long itemId, int value, long contributorId) {
        jdbc.update("INSERT INTO price_obs (ut_item_id, platform, observed_at, kind, value, contributor_id) VALUES (?, 'CONSOLE', now(), 'BIN', ?, ?)", itemId, value, contributorId);
    }

    private void aggregate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ut/prices/aggregate").header("Authorization", "Bearer " + adminToken())).andReturn();
    }

    @Test
    void definitionIdIsAcceptedAndSkippedPricesAreCountedForAdmins() throws Exception {
        String definitionOnly = auction(TOTW_RESOURCE, 91, 5000, 3000).replace("\"resourceId\":" + TOTW_RESOURCE, "\"definitionId\":" + TOTW_RESOURCE);
        contribute("/transfermarket", definitionOnly, auction(66_666_666L, 91, 5000, 3000), auction(66_666_667L, 91, 5000, 3000));

        assertThat(count(totwItem)).isEqualTo(1);
        JsonNode stats = call(get("/api/v1/admin/ut/price-stats").header("Authorization", "Bearer " + adminToken()), null, null, 200).at("/data/0");
        assertThat(stats.at("/recorded").asInt()).isEqualTo(1);
        assertThat(stats.at("/skippedUnknownItem").asInt()).isEqualTo(2);
    }

    private String adminBearer;

    private String adminToken() throws Exception {
        if (adminBearer == null) {
            adminBearer = com.fcareer.AdminTestSupport.adminToken(mockMvc, jdbc, objectMapper);
        }
        return adminBearer;
    }

    @Test
    void aCaptureClockFarFromTheServerFallsBackToServerTimeAndIsCounted() throws Exception {
        contributeAt(Instant.now().minus(java.time.Duration.ofDays(400)).toEpochMilli(), "/transfermarket", auction(TOTW_RESOURCE, 91, 5000, 3000));

        long observed = jdbc.queryForObject("SELECT extract(epoch FROM observed_at)::bigint FROM price_obs", Long.class);
        assertThat(Math.abs(observed - Instant.now().getEpochSecond())).isLessThan(120);
        JsonNode stats = call(get("/api/v1/admin/ut/price-stats").header("Authorization", "Bearer " + adminToken()), null, null, 200).at("/data/0");
        assertThat(stats.at("/clockSkew").asInt()).isEqualTo(1);
    }

    private int count(long itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM price_obs WHERE ut_item_id = ?", Integer.class, itemId);
    }

    private long register() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", "price-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200);
        return call(get("/api/v1/me"), null, registered.at("/data/accessToken").asText(), 200).at("/data/id").asLong();
    }

    private String login(long userId) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, userId);
        return call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getContentAsString());
    }
}
