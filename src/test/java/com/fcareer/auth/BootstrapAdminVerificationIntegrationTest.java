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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * REVIEW F7: e-posta doğrulaması açıkken bootstrap yönetici e-postasıyla önce kaydolan biri yönetici olmamalı; rol yalnız e-posta
 * doğrulanınca verilir (adresin sahibi olduğu kanıtlanınca).
 */
@IntegrationTest
@TestPropertySource(properties = {"fcareer.auth.email-verification.enabled=true", "fcareer.auth.bootstrap-admin-email=verify-admin@example.com"})
class BootstrapAdminVerificationIntegrationTest {

    private static final String EMAIL = "verify-admin@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OutboxVerificationMailer outbox;

    @Test
    void theBootstrapRoleIsGrantedOnlyAfterTheEmailIsVerified() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE email = ?", EMAIL);
        String token = call(post("/api/v1/auth/register"), Map.of("email", EMAIL, "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
        assertThat(call(get("/api/v1/me"), null, token, 200).at("/data/role").asText()).isEqualTo("USER");
        call(get("/api/v1/admin/ut/queue"), null, token, 403);

        String verification = outbox.messages().stream().filter(m -> m.to().equals(EMAIL)).reduce((a, b) -> b).orElseThrow().token();
        call(post("/api/v1/auth/verify-email"), Map.of("token", verification), null, 200);
        String fresh = call(post("/api/v1/auth/login"), Map.of("email", EMAIL, "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        assertThat(call(get("/api/v1/me"), null, fresh, 200).at("/data/role").asText()).isEqualTo("ADMIN");
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
