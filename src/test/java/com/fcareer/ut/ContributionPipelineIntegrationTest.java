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
