package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class ContributionPipelineIntegrationTest {

    private static final String UT = "https://utas.mob.v1.prd.futc-ext.gcp.ea.com/ut/game/fc27";
    private static final long SET_ID = 9501;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.fcareer.ut.kb.ContributionService contributionService;

    private String token;

    @BeforeEach
    void contributor() throws Exception {
        jdbc.update("DELETE FROM kb_text");
        jdbc.update("DELETE FROM contrib_queue");
        jdbc.update("DELETE FROM contrib_event");
        jdbc.update("DELETE FROM sbc_challenge");
        jdbc.update("DELETE FROM sbc_set");
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", "pipe-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200);
        long user = call(get("/api/v1/me"), null, registered.at("/data/accessToken").asText(), 200).at("/data/id").asLong();
        jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100)", user);
        token = registered.at("/data/accessToken").asText();
    }

    private static String hub(String name, long endTime, String extra) {
        return "{\"categories\":[{\"categoryId\":1,\"name\":\"Special\",\"sets\":[{\"setId\":" + SET_ID + ",\"name\":\"" + name + "\",\"challengesCount\":1,\"challengesCompletedCount\":0,"
                + "\"repeatable\":true," + extra + "\"endTime\":" + endTime + ",\"awards\":[{\"type\":\"pack\",\"value\":519,\"count\":2}]}]}]}";
    }

    private static final String CHALLENGE = "{\"challenges\":[{\"name\":\"Brick\",\"status\":\"NOT_STARTED\",\"setId\":" + SET_ID + ",\"challengeId\":95011,\"repeatable\":true,\"formation\":\"f451\","
            + "\"type\":\"ONE_CLICK\",\"scoreRequirement\":4200,\"timesCompleted\":0,\"elgReq\":[],\"awards\":[]}]}";

    private void contribute(long capturedAtMillis, String hubBody) throws Exception {
        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", new Object[]{
                Map.of("url", UT + "/sbs/hub/v2", "ts", capturedAtMillis, "body", hubBody),
                Map.of("url", UT + "/sbs/setid/" + SET_ID + "/challenges", "ts", capturedAtMillis + 1, "body", CHALLENGE)}), token, 200);
    }

    private Instant endsAt() {
        return jdbc.queryForObject("SELECT ends_at FROM sbc_set WHERE id = ?", java.sql.Timestamp.class, SET_ID).toInstant();
    }

    @Test
    void setRewardItemScoreTargetAndRepeatCountAreStored() throws Exception {
        contribute(Instant.now().toEpochMilli(), hub("Marquee", 86_400, "\"repeats\":3,"));

        assertThat(jdbc.queryForObject("SELECT reward::text FROM sbc_set WHERE id = ?", String.class, SET_ID)).contains("519");
        assertThat(jdbc.queryForObject("SELECT item_score_target FROM sbc_challenge WHERE id = 95011", Integer.class)).isEqualTo(4200);
        assertThat(jdbc.queryForObject("SELECT repeatable FROM sbc_set WHERE id = ?", Integer.class, SET_ID)).isEqualTo(3);
    }

    /** V3-05d-b: formasyon katkı yolunda kaybolmaz (tek eşleme), tanınmayan kod ham saklanır; EA görev tipi saklanır, ONE_CLICK → SINGLE_ITEM, BRICK kadro görevi. */
    @Test
    void formationTypeAndKindSurviveTheContributionPath() throws Exception {
        String challenges = "{\"challenges\":["
                + "{\"challengeId\":95021,\"name\":\"open\",\"setId\":" + SET_ID + ",\"formation\":\"f41212\",\"type\":\"OPEN_CHALLENGE\",\"elgReq\":[],\"awards\":[]},"
                + "{\"challengeId\":95022,\"name\":\"new\",\"setId\":" + SET_ID + ",\"formation\":\"f999\",\"type\":\"OPEN_CHALLENGE\",\"elgReq\":[],\"awards\":[]},"
                + "{\"challengeId\":95023,\"name\":\"one\",\"setId\":" + SET_ID + ",\"type\":\"ONE_CLICK_CHALLENGE\",\"scoreRequirement\":4000,\"elgReq\":["
                + "{\"type\":\"PLAYER_ATTRIBUTE\",\"eligibilitySlot\":1,\"eligibilityKey\":41,\"eligibilityValue\":1},{\"type\":\"ACADEMY_PLAYER_SLOTTING\",\"eligibilitySlot\":1,\"eligibilityKey\":40,\"eligibilityValue\":45},"
                + "{\"type\":\"SCOPE\",\"eligibilitySlot\":1,\"eligibilityKey\":13,\"eligibilityValue\":0}],\"awards\":[]},"
                + "{\"challengeId\":95024,\"name\":\"brick\",\"setId\":" + SET_ID + ",\"formation\":\"f442\",\"type\":\"BRICK_CHALLENGE\",\"elgReq\":[],\"awards\":[]}]}";
        long ts = Instant.now().toEpochMilli();
        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", new Object[]{
                Map.of("url", UT + "/sbs/hub/v2", "ts", ts, "body", hub("Set", 86_400, "")),
                Map.of("url", UT + "/sbs/setid/" + SET_ID + "/challenges", "ts", ts + 1, "body", challenges)}), token, 200);

        Map<String, Object> open = jdbc.queryForMap("SELECT formation, ea_formation, ea_type, kind FROM sbc_challenge WHERE id = 95021");
        assertThat(open).containsEntry("formation", "4-1-2-1-2 (geniş)").containsEntry("ea_formation", "f41212").containsEntry("ea_type", "OPEN_CHALLENGE").containsEntry("kind", "SQUAD");
        Map<String, Object> unknown = jdbc.queryForMap("SELECT formation, ea_formation FROM sbc_challenge WHERE id = 95022");
        assertThat(unknown.get("formation")).isNull();
        assertThat(unknown.get("ea_formation")).isEqualTo("f999");
        Map<String, Object> one = jdbc.queryForMap("SELECT kind, ea_type, item_score_target FROM sbc_challenge WHERE id = 95023");
        assertThat(one).containsEntry("kind", "SINGLE_ITEM").containsEntry("ea_type", "ONE_CLICK_CHALLENGE").containsEntry("item_score_target", 4000);
        assertThat(jdbc.queryForMap("SELECT formation, kind, ea_type FROM sbc_challenge WHERE id = 95024"))
                .containsEntry("formation", "4-4-2").containsEntry("kind", "SQUAD").containsEntry("ea_type", "BRICK_CHALLENGE");

        JsonNode set = call(get("/api/v1/ut/kb/sbc/" + SET_ID), null, null, 200).at("/data/challenges");
        assertThat(set.findValuesAsText("eaFormation")).contains("f41212", "f999");
        assertThat(set.findValuesAsText("kind")).contains("SINGLE_ITEM");
    }

    private static final String ONE_CLICK = "{\"challenges\":[{\"challengeId\":95031,\"name\":\"one\",\"setId\":" + SET_ID + ",\"type\":\"ONE_CLICK_CHALLENGE\",\"scoreRequirement\":4000,"
            + "\"elgReq\":[],\"awards\":[]},{\"challengeId\":95032,\"name\":\"open\",\"setId\":" + SET_ID + ",\"formation\":\"f532\",\"type\":\"OPEN_CHALLENGE\",\"elgReq\":[],\"awards\":[]}]}";

    private void contributeChallenges(String bearer, String body) throws Exception {
        long ts = Instant.now().toEpochMilli();
        call(post("/api/v1/ut/contrib/capture"), Map.of("captures", new Object[]{
                Map.of("url", UT + "/sbs/hub/v2", "ts", ts, "body", hub("Set", 86_400, "")),
                Map.of("url", UT + "/sbs/setid/" + SET_ID + "/challenges", "ts", ts + 1, "body", body)}), bearer, 200);
    }

    private String secondContributor() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", "pipe2-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200);
        long user = call(get("/api/v1/me"), null, registered.at("/data/accessToken").asText(), 200).at("/data/id").asLong();
        jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100)", user);
        return registered.at("/data/accessToken").asText();
    }

    /**
     * V3-05d-b: türetilmiş alanlar (kind, formasyon etiketi) bizim kodumuzun ham alanlardan (type, formation) çıkardığı değerlerdir; içerik kimliğine girmez.
     * Eski kodla yazılmış satır (eski kind, kayıp formasyon, eski tanımlı hash) aynı içerik tekrar gelince çatışma sayılmaz: türetilmiş sütunlar düzelir,
     * hash yeni tanıma geçer, o anahtar için kendiliğinden açılmış kuyruk kapanır.
     */
    @Test
    void derivedFieldsConvergeWithoutAConflictWhenTheSameContentArrivesAgain() throws Exception {
        contributeChallenges(token, ONE_CLICK);
        JsonNode payload = objectMapper.readTree(jdbc.queryForObject("SELECT payload::text FROM contrib_event WHERE natural_key = 'SBC_CHALLENGE:95031' ORDER BY id DESC LIMIT 1", String.class));
        String legacy = contributionService.legacyChallengeHash((com.fasterxml.jackson.databind.node.ObjectNode) payload, "SQUAD");
        jdbc.update("UPDATE sbc_challenge SET kind = 'SQUAD', ea_type = NULL, content_hash = ? WHERE id = 95031", legacy);
        jdbc.update("UPDATE sbc_challenge SET formation = NULL, ea_formation = NULL WHERE id = 95032");
        jdbc.update("INSERT INTO contrib_queue (kind, natural_key, candidates) VALUES ('SBC_CHALLENGE', 'SBC_CHALLENGE:95031', '[]'::jsonb)");

        contributeChallenges(secondContributor(), ONE_CLICK);

        assertThat(jdbc.queryForMap("SELECT kind, ea_type FROM sbc_challenge WHERE id = 95031")).containsEntry("kind", "SINGLE_ITEM").containsEntry("ea_type", "ONE_CLICK_CHALLENGE");
        assertThat(jdbc.queryForObject("SELECT content_hash FROM sbc_challenge WHERE id = 95031", String.class)).isNotEqualTo(legacy);
        assertThat(jdbc.queryForMap("SELECT formation, ea_formation FROM sbc_challenge WHERE id = 95032")).containsEntry("formation", "5-3-2").containsEntry("ea_formation", "f532");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrib_queue WHERE status = 'OPEN'", Integer.class)).isZero();
    }

    @Test
    void storedRewardKeepsOnlyAllowlistedCardFields() throws Exception {
        String itemHub = hub("Player", 86_400, "").replace("\"awards\":[{", "\"awards\":[{\"type\":\"item\",\"value\":50000777,\"count\":1,\"itemData\":" + RewardItemFixture.ITEM_DATA.strip() + "},{");
        contribute(Instant.now().toEpochMilli(), itemHub);

        String reward = jdbc.queryForObject("SELECT reward::text FROM sbc_set WHERE id = ?", String.class, SET_ID);
        assertThat(reward).contains("\"assetId\": 777").contains("519").doesNotContain("900000000001");
        RewardItemFixture.FORBIDDEN.forEach(f -> assertThat(reward).as(f).doesNotContain(f));
    }

    @Test
    void endsAtComesFromTheCaptureTimeNotTheArrivalTime() throws Exception {
        Instant captured = Instant.now().minusSeconds(7_200);
        contribute(captured.toEpochMilli(), hub("Marquee", 86_400, ""));

        assertThat(endsAt().getEpochSecond()).isCloseTo(captured.plusSeconds(86_400).getEpochSecond(), within(2L));
    }

    @Test
    void unchangedContentStillRefreshesEndsAt() throws Exception {
        Instant first = Instant.now().minusSeconds(7_200);
        contribute(first.toEpochMilli(), hub("Marquee", 86_400, ""));
        Instant second = first.plusSeconds(3_600);
        contribute(second.toEpochMilli(), hub("Marquee", 3_600, ""));

        assertThat(endsAt().getEpochSecond()).isCloseTo(second.plusSeconds(3_600).getEpochSecond(), within(2L));
    }

    private String newToken() throws Exception {
        JsonNode registered = call(post("/api/v1/auth/register"), Map.of("email", "pipe-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200);
        String bearer = registered.at("/data/accessToken").asText();
        long user = call(get("/api/v1/me"), null, bearer, 200).at("/data/id").asLong();
        jdbc.update("INSERT INTO contributor (user_id, trust) VALUES (?, 100)", user);
        return bearer;
    }

    private JsonNode contributeAs(String bearer, String locale, String hubBody, String challengeBody) throws Exception {
        long ts = Instant.now().toEpochMilli();
        return call(post("/api/v1/ut/contrib/capture" + (locale == null ? "" : "?locale=" + locale)), Map.of("captures", new Object[]{
                Map.of("url", UT + "/sbs/hub/v2", "ts", ts, "body", hubBody),
                Map.of("url", UT + "/sbs/setid/" + SET_ID + "/challenges", "ts", ts + 1, "body", challengeBody)}), bearer, 200);
    }

    @Test
    void localizedNamesDoNotSplitConsensusAndAreKeptPerLocale() throws Exception {
        contributeAs(token, "en-GB", hub("Marquee Matchups", 86_400, ""), CHALLENGE.replace("Brick", "Brick EN"));
        JsonNode turkish = contributeAs(token, "tr-TR", hub("Marquee Eşleşmeleri", 86_400, ""), CHALLENGE.replace("Brick", "Tuğla TR"));

        assertThat(turkish.at("/data/unchanged").asInt()).isEqualTo(2);
        assertThat(turkish.at("/data/queued").asInt()).isZero();
        assertThat(jdbc.queryForList("SELECT locale FROM kb_text WHERE natural_key = ? ORDER BY locale", String.class, "SBC_SET:" + SET_ID)).containsExactly("en-GB", "tr-TR");
        assertThat(jdbc.queryForObject("SELECT name FROM kb_text WHERE natural_key = ? AND locale = 'tr-TR'", String.class, "SBC_SET:" + SET_ID)).isEqualTo("Marquee Eşleşmeleri");
    }

    @Test
    void conflictingCandidatesAccumulateInTheQueue() throws Exception {
        String first = newToken();
        String second = newToken();
        String third = newToken();
        contributeAs(first, null, hub("Marquee", 86_400, ""), CHALLENGE);
        contributeAs(second, null, hub("Marquee", 86_400, ""), CHALLENGE.replace("4200", "4300"));
        JsonNode queued = contributeAs(third, null, hub("Marquee", 86_400, ""), CHALLENGE.replace("4200", "4400"));

        assertThat(queued.at("/data/queued").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(candidates) FROM contrib_queue WHERE status = 'OPEN'", Integer.class)).isEqualTo(3);
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
