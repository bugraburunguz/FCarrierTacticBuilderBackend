package com.fcareer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.auth.mail.OutboxVerificationMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@TestPropertySource(properties = "fcareer.auth.email-verification.enabled=true")
class EmailVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OutboxVerificationMailer outbox;

    private String register(String email) throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
    }

    private String lastTokenFor(String email) {
        return outbox.messages().stream().filter(m -> m.to().equals(email)).reduce((a, b) -> b).orElseThrow().token();
    }

    @Test
    void unverifiedAccountsGetNoCreditUntilTheLinkIsUsed() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        String bearer = register(email);

        JsonNode me = call(get("/api/v1/me"), null, bearer, 200).at("/data");
        assertThat(me.at("/emailVerified").asBoolean()).isFalse();
        assertThat(me.at("/emailVerificationRequired").asBoolean()).isTrue();
        assertThat(me.at("/creditBalance").asInt()).isZero();

        String token = lastTokenFor(email);
        call(post("/api/v1/auth/verify-email"), Map.of("token", token), null, 200);

        JsonNode verified = call(get("/api/v1/me"), null, bearer, 200).at("/data");
        assertThat(verified.at("/emailVerified").asBoolean()).isTrue();
        assertThat(verified.at("/creditBalance").asInt()).isEqualTo(verified.at("/dailyCreditLimit").asInt()).isPositive();

        call(post("/api/v1/auth/verify-email"), Map.of("token", token), null, 400);
        call(post("/api/v1/auth/verify-email"), Map.of("token", "yok-boyle-bir-token"), null, 400);
    }

    @Test
    void resendIsThrottledAndIssuesAFreshToken() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        String bearer = register(email);
        String first = lastTokenFor(email);

        call(post("/api/v1/me/email/resend"), null, bearer, 429);

        jdbc.update("UPDATE email_verification_token SET created_at = now() - interval '5 minutes' WHERE user_id = (SELECT id FROM app_user WHERE email = ?)", email);
        call(post("/api/v1/me/email/resend"), null, bearer, 200);
        String second = lastTokenFor(email);

        assertThat(second).isNotEqualTo(first);
        call(post("/api/v1/auth/verify-email"), Map.of("token", first), null, 400);
        call(post("/api/v1/auth/verify-email"), Map.of("token", second), null, 200);
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
