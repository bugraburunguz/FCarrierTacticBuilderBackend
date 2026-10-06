package com.fcareer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.billing.service.CreditService;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class ApiFlowIntegrationTest {

    private static final String PASSWORD = "S3cure-pass!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private CreditService creditService;
    @Autowired
    private CreditGuard creditGuard;

    @BeforeEach
    void seedCatalog() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void registerLoginRefreshAndRotation() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", email, "password", PASSWORD), null, 200);
        assertThat(registered.at("/success").asBoolean()).isTrue();

        JsonNode duplicate = call(post("/api/v1/auth/register"), Map.of("email", email.toUpperCase(), "password", PASSWORD), null, 200);
        assertThat(duplicate.at("/success").asBoolean()).isFalse();
        assertThat(duplicate.at("/error/0/code").asText()).isEqualTo("fc.exception.0006");

        JsonNode badLogin = call(post("/api/v1/auth/login"), Map.of("email", email, "password", "wrong-password"), null, 200);
        assertThat(badLogin.at("/error/0/code").asText()).isEqualTo("fc.exception.0007");

        JsonNode login = call(post("/api/v1/auth/login"), Map.of("email", email, "password", PASSWORD), null, 200);
        String refresh = login.at("/data/refreshToken").asText();
        JsonNode rotated = call(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh), null, 200);
        assertThat(rotated.at("/data/accessToken").asText()).isNotBlank();

        JsonNode reused = call(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh), null, 200);
        assertThat(reused.at("/error/0/code").asText()).isEqualTo("fc.exception.0008");

        call(post("/api/v1/auth/register"), Map.of("email", "not-an-email", "password", PASSWORD), null, 400);
    }

    @Test
    void protectedEndpointsRequireTokenAndAdminRequiresKey() throws Exception {
        call(get("/api/v1/me"), null, null, 401);
        call(get("/api/v1/me"), null, "garbage.token.value", 401);
        call(post("/api/v1/admin/ingestion/run"), null, null, 403);
        JsonNode admin = call(post("/api/v1/admin/ingestion/run").header("X-Admin-Key", "test-key"), null, null, 200);
        assertThat(admin.at("/success").asBoolean()).isTrue();
        call(get("/api/v1/roles"), null, null, 200);
    }

    @Test
    void freeUserSpendsCreditsAndRefundsOnFailure() throws Exception {
        String token = registerAndGetToken();
        JsonNode me = call(get("/api/v1/me"), null, token, 200);
        long userId = me.at("/data/id").asLong();
        assertThat(me.at("/data/creditBalance").asInt()).isEqualTo(20);
        assertThat(me.at("/data/showAds").asBoolean()).isTrue();

        long careerId = createCareer(token, 0).at("/data/id").asLong();
        Map<String, Object> body = Map.of("careerId", careerId, "tactic", Map.of("formation", "4-4-2"));

        JsonNode first = call(post("/api/v1/fit/squad"), body, token, 200);
        assertThat(first.at("/data/charged").asInt()).isEqualTo(5);
        assertThat(first.at("/data/balance").asInt()).isEqualTo(15);
        assertThat(first.at("/data/result/slots")).hasSize(11);
        assertThat(first.at("/data/result/summary").asText()).contains("Kadro taktiğe");

        JsonNode invalid = call(post("/api/v1/fit/squad"), Map.of("careerId", careerId, "tactic", Map.of("formation", "9-9-9")), token, 200);
        assertThat(invalid.at("/error/0/code").asText()).isEqualTo("fc.exception.0002");
        assertThat(call(get("/api/v1/me"), null, token, 200).at("/data/creditBalance").asInt()).isEqualTo(15);

        assertThatThrownBy(() -> creditGuard.runCharged(userId, 5, "TEST", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(creditService.status(userId).balance()).isEqualTo(15);

        for (int i = 0; i < 3; i++) {
            assertThat(call(post("/api/v1/fit/squad"), body, token, 200).at("/data/charged").asInt()).isEqualTo(5);
        }
        JsonNode exhausted = call(post("/api/v1/fit/squad"), body, token, 200);
        assertThat(exhausted.at("/error/0/code").asText()).isEqualTo("fc.exception.0004");
    }

    @Test
    void premiumUserIsNotCharged() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 0).at("/data/id").asLong();

        JsonNode subscription = call(post("/api/v1/billing/subscribe"), null, token, 200);
        assertThat(subscription.at("/data/status").asText()).isEqualTo("ACTIVE");

        JsonNode me = call(get("/api/v1/me"), null, token, 200);
        assertThat(me.at("/data/subscriptionType").asText()).isEqualTo("PREMIUM");
        assertThat(me.at("/data/showAds").asBoolean()).isFalse();
        assertThat(me.at("/data/creditBalance").isMissingNode()).isTrue();

        JsonNode fit = call(post("/api/v1/fit/squad"), Map.of("careerId", careerId, "tactic", Map.of("formation", "4-3-3")), token, 200);
        assertThat(fit.at("/data/charged").asInt()).isZero();
    }

    @Test
    void careerEventsAreEventSourcedAndOwned() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 5_000_000).at("/data/id").asLong();
        JsonNode squad = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200);
        long ownPlayer = squad.at("/data/0/player/id").asLong();
        long outsiderId = firstPlayerNotInSquad(squad);

        JsonNode tooExpensive = call(post("/api/v1/careers/" + careerId + "/events"),
                Map.of("type", "BUY", "playerId", outsiderId, "feeEur", 9_000_000), token, 200);
        assertThat(tooExpensive.at("/error/0/code").asText()).isEqualTo("fc.exception.0011");

        JsonNode bought = call(post("/api/v1/careers/" + careerId + "/events"),
                Map.of("type", "BUY", "playerId", outsiderId, "feeEur", 2_000_000), token, 200);
        assertThat(bought.at("/data/budgetEurAfter").asLong()).isEqualTo(3_000_000);

        JsonNode duplicate = call(post("/api/v1/careers/" + careerId + "/events"),
                Map.of("type", "BUY", "playerId", outsiderId, "feeEur", 1), token, 200);
        assertThat(duplicate.at("/error/0/code").asText()).isEqualTo("fc.exception.0010");

        JsonNode sold = call(post("/api/v1/careers/" + careerId + "/events"),
                Map.of("type", "SELL", "playerId", ownPlayer, "feeEur", 1_000_000), token, 200);
        assertThat(sold.at("/data/budgetEurAfter").asLong()).isEqualTo(4_000_000);
        assertThat(call(get("/api/v1/careers/" + careerId + "/events"), null, token, 200).at("/data")).hasSize(2);

        String stranger = registerAndGetToken();
        JsonNode denied = call(get("/api/v1/careers/" + careerId), null, stranger, 200);
        assertThat(denied.at("/error/0/code").asText()).isEqualTo("fc.exception.0009");
    }

    @Test
    void roleFitCompareAndRecommendationEndpoints() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 0).at("/data/id").asLong();
        long playerId = call(get("/api/v1/players?size=1"), null, null, 200).at("/data/items/0/id").asLong();

        JsonNode detail = call(get("/api/v1/players/" + playerId), null, null, 200);
        assertThat(detail.at("/data/positionOveralls").size()).isIn(1, 11);
        detail.at("/data/positionOveralls").forEach(v -> assertThat(v.asInt()).isBetween(1, 99));

        JsonNode fit = call(post("/api/v1/fit/role"), Map.of("playerId", playerId, "roleId", "winger_attack",
                "position", "RW", "tags", new String[]{"w_cross"}), null, 200);
        assertThat(fit.at("/data/roleFit/score").asDouble()).isBetween(0.0, 100.0);
        assertThat(fit.at("/data/intentFit/perTag/0/tagId").asText()).isEqualTo("w_cross");

        JsonNode conflict = call(post("/api/v1/fit/role"), Map.of("playerId", playerId, "roleId", "winger_attack",
                "tags", new String[]{"w_cross", "w_cut_inside"}), null, 200);
        assertThat(conflict.at("/error/0/code").asText()).isEqualTo("fc.exception.0003");

        JsonNode compare = call(post("/api/v1/players/compare"), Map.of("ids", new long[]{playerId}, "roleId", "poacher_attack"), null, 200);
        assertThat(compare.at("/data")).hasSize(1);

        JsonNode similar = call(get("/api/v1/players/" + playerId + "/similar?limit=5"), null, null, 200);
        assertThat(similar.at("/success").asBoolean()).as(similar.toString()).isTrue();
        assertThat(similar.at("/data").isArray()).isTrue();
        similar.at("/data").forEach(s -> assertThat(s.at("/player/id").asLong()).isNotEqualTo(playerId));

        JsonNode roles = call(get("/api/v1/players/" + playerId + "/roles"), null, null, 200);
        assertThat(roles.at("/success").asBoolean()).as(roles.toString()).isTrue();
        assertThat(roles.at("/data")).isNotEmpty();
        roles.at("/data").forEach(r -> {
            assertThat(r.at("/score").asDouble()).isBetween(0.0, 100.0);
            assertThat(r.at("/gaps").isArray()).isTrue();
        });

        JsonNode setupCheck = call(post("/api/v1/fit/setup-check"), Map.of("careerId", careerId, "buildUp", "Short", "depth", 80), token, 200);
        assertThat(setupCheck.at("/success").asBoolean()).as(setupCheck.toString()).isTrue();
        assertThat(setupCheck.at("/data")).hasSize(3);
        setupCheck.at("/data").forEach(c -> assertThat(c.at("/status").asText()).isIn("GREEN", "YELLOW", "RED"));

        JsonNode careerDetail = call(get("/api/v1/players/" + playerId + "?career=" + careerId), null, token, 200);
        assertThat(careerDetail.at("/success").asBoolean()).as(careerDetail.toString()).isTrue();
        assertThat(careerDetail.at("/data/summary/id").asLong()).isEqualTo(playerId);

        JsonNode withSetup = call(post("/api/v1/fit/lineup"), Map.of("careerId", careerId, "tactic",
                Map.of("formation", "4-3-3", "setup", Map.of("buildUp", "Short", "depth", 90))), token, 200);
        assertThat(withSetup.at("/success").asBoolean()).as(withSetup.toString()).isTrue();
        assertThat(withSetup.at("/data/setupDelta").isNumber()).isTrue();

        JsonNode exported = call(post("/api/v1/tactics/export-code"), Map.of("formation", "4-4-2",
                "setup", Map.of("buildUp", "Counter", "depth", 60)), null, 200);
        assertThat(exported.at("/success").asBoolean()).as(exported.toString()).isTrue();
        String exportedCode = exported.at("/data/code").asText();
        assertThat(exportedCode).hasSize(12);
        JsonNode imported = call(post("/api/v1/tactics/import-code"), Map.of("code", exportedCode), null, 200);
        assertThat(imported.at("/success").asBoolean()).as(imported.toString()).isTrue();
        assertThat(imported.at("/data/formation").asText()).isEqualTo("4-4-2");
        assertThat(imported.at("/data/buildUp").asText()).isEqualTo("Counter");
        assertThat(imported.at("/data/depth").asInt()).isEqualTo(60);
        assertThat(imported.at("/data/slots")).hasSize(11);
        JsonNode badCode = call(post("/api/v1/tactics/import-code"), Map.of("code", "AAAAAAAAAAAA"), null, 200);
        assertThat(badCode.at("/success").asBoolean()).isFalse();

        JsonNode preview = call(post("/api/v1/recommend/preview"), Map.of("roleId", "winger_attack", "position", "RW"), null, 200);
        assertThat(preview.at("/success").asBoolean()).isTrue();
        assertThat(preview.at("/data/items").isArray()).isTrue();

        JsonNode recommended = call(post("/api/v1/recommend/slot"), Map.of("careerId", careerId, "roleId", "winger_attack",
                "position", "RW", "limit", 5), token, 200);
        assertThat(recommended.at("/success").asBoolean()).isTrue();
        assertThat(recommended.at("/data/charged").asInt()).isEqualTo(3);
        assertThat(recommended.at("/data/result/items").isArray()).isTrue();
        assertThat(recommended.at("/data/result/scoutQuery/minAttrs/Crossing").asInt()).isEqualTo(64);

        for (String scope : new String[]{"SQUAD", "LEAGUE", "MARKET"}) {
            JsonNode view = call(post("/api/v1/recommend/slot-view"), Map.of("careerId", careerId, "scope", scope,
                    "slot", Map.of("roleId", "winger_attack", "position", "RW", "leagueId", 1)), token, 200);
            assertThat(view.at("/success").asBoolean()).as(scope).isTrue();
            assertThat(view.at("/data/items").isArray()).as(scope).isTrue();
        }
        JsonNode unauthenticated = call(post("/api/v1/recommend/slot-view"), Map.of("careerId", careerId, "scope", "SQUAD",
                "slot", Map.of("roleId", "winger_attack", "position", "RW")), null, 401);
        assertThat(unauthenticated.at("/success").asBoolean()).isFalse();
        JsonNode targets = call(post("/api/v1/recommend/transfer-targets"), Map.of("careerId", careerId, "perPosition", 3,
                "slots", java.util.List.of(Map.of("position", "RW", "roleId", "winger_attack"), Map.of("position", "RW", "roleId", "winger_attack"),
                        Map.of("position", "ST", "roleId", "poacher_attack"))), token, 200);
        assertThat(targets.at("/success").asBoolean()).as(targets.toString()).isTrue();
        assertThat(targets.at("/data/positions")).hasSize(2);
        targets.at("/data/positions/0/targets").forEach(t -> assertThat(t.at("/acquisition").asText()).isIn("FREE", "LOAN", "BUY"));
    }

    @Test
    void advisorPicksSetupAndExplainsPhilosophyGap() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 0).at("/data/id").asLong();

        JsonNode advice = call(post("/api/v1/advisor/squad"), Map.of("careerId", careerId, "philosophyPresetId", "tiki_taka",
                "philosophyWeight", 0, "allowTransfers", true), token, 200);
        assertThat(advice.at("/success").asBoolean()).as(advice.toString()).isTrue();
        assertThat(advice.at("/data/charged").asInt()).isEqualTo(8);
        JsonNode result = advice.at("/data/result");
        assertThat(result.at("/recommended/lineup/slots")).hasSize(11);
        assertThat(result.at("/recommended/tactic/slots")).hasSize(11);
        assertThat(result.at("/playerPositions").size()).isGreaterThan(10);
        assertThat(result.at("/evaluated").asInt()).isGreaterThan(30);
        assertThat(result.at("/verdict").asText()).isIn("BEST_FIT", "PHILOSOPHY_OK", "BETTER_ELSEWHERE");
        assertThat(result.at("/transferSuggestions").isArray()).isTrue();
    }

    @Test
    void autoFitRecommendsFormationsPresetsAndTwoDifferentCombos() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 0).at("/data/id").asLong();

        JsonNode presets = call(get("/api/v1/presets?kind=PERSONAL"), null, null, 200);
        assertThat(presets.at("/data/0/id").asText()).isEqualTo("personal_cakal_doktrini");

        JsonNode response = call(post("/api/v1/advisor/tactic"), Map.of("careerId", careerId), token, 200);
        assertThat(response.at("/success").asBoolean()).as(response.toString()).isTrue();
        assertThat(response.at("/data/charged").asInt()).isEqualTo(8);
        JsonNode result = response.at("/data/result");
        assertThat(result.at("/formations").size()).isBetween(1, 5);
        assertThat(result.at("/presets").size()).isBetween(1, 5);
        assertThat(result.at("/bestCombos")).hasSize(2);
        JsonNode first = result.at("/bestCombos/0");
        JsonNode second = result.at("/bestCombos/1");
        assertThat(first.at("/formation").asText() + first.at("/presetId").asText())
                .isNotEqualTo(second.at("/formation").asText() + second.at("/presetId").asText());
        assertThat(first.at("/lineup/slots")).hasSize(11);
        assertThat(first.at("/pct").asInt()).isGreaterThanOrEqualTo(second.at("/pct").asInt());

        JsonNode locked = call(post("/api/v1/advisor/tactic"), Map.of("careerId", careerId, "lockFormation", "4-4-2",
                "diversityMode", "differentPreset"), token, 200);
        JsonNode lockedCombos = locked.at("/data/result/bestCombos");
        assertThat(lockedCombos.get(0).at("/formation").asText()).isEqualTo("4-4-2");
        assertThat(lockedCombos.get(1).at("/formation").asText()).isEqualTo("4-4-2");
        assertThat(lockedCombos.get(0).at("/presetId").asText()).isNotEqualTo(lockedCombos.get(1).at("/presetId").asText());
    }

    @Test
    void lineupIsFreeAndReturnsStartersWithBackups() throws Exception {
        String token = registerAndGetToken();
        long careerId = createCareer(token, 0).at("/data/id").asLong();
        int before = call(get("/api/v1/me"), null, token, 200).at("/data/creditBalance").asInt();

        JsonNode lineup = call(post("/api/v1/fit/lineup"), Map.of("careerId", careerId, "tactic", Map.of("formation", "4-3-3")), token, 200);

        assertThat(lineup.at("/success").asBoolean()).as(lineup.toString()).isTrue();
        assertThat(lineup.at("/data/slots")).hasSize(11);
        assertThat(lineup.at("/data/bench").isArray()).isTrue();
        assertThat(lineup.at("/data/slots/0/depth").isArray()).isTrue();
        assertThat(call(get("/api/v1/me"), null, token, 200).at("/data/creditBalance").asInt()).isEqualTo(before);
    }

    private JsonNode createCareer(String token, long budget) throws Exception {
        long clubId = clubRepository.findAll().get(0).getId();
        JsonNode career = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", budget), token, 200);
        assertThat(career.at("/success").asBoolean()).isTrue();
        return career;
    }

    private long firstPlayerNotInSquad(JsonNode squad) {
        java.util.Set<Long> inSquad = new java.util.HashSet<>();
        squad.at("/data").forEach(p -> inSquad.add(p.at("/player/id").asLong()));
        int squadGender = squad.at("/data/0/player/gender").asInt();
        return basePlayerRepository.findAll().stream()
                .filter(p -> p.getGender() == squadGender && !inSquad.contains(p.getId()))
                .map(p -> p.getId()).findFirst().orElseThrow();
    }

    private String registerAndGetToken() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", uniqueEmail(), "password", PASSWORD), null, 200);
        return registered.at("/data/accessToken").asText();
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus()).as(builder.toString()).isEqualTo(expectedStatus);
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
