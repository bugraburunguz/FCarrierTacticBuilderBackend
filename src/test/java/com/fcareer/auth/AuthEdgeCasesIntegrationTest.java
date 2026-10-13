package com.fcareer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** BE-SEC-08: logout replay'i oturum ailesini düşürmez, refresh token uzunluğu sınırlı, çok baytlı uzun şifre 500 değil 400. */
@IntegrationTest
class AuthEdgeCasesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    void replayingALoggedOutTokenDoesNotRevokeTheOtherSessions() throws Exception {
        String email = "edge-" + UUID.randomUUID() + "@example.com";
        String firstRefresh = call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!"), 200).at("/data/refreshToken").asText();
        String secondRefresh = call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), 200).at("/data/refreshToken").asText();

        call(post("/api/v1/auth/logout"), Map.of("refreshToken", firstRefresh), 200);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", firstRefresh), 401);

        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", secondRefresh), 200);
    }

    @Test
    void oversizedRefreshTokensAreRejectedBeforeHashing() throws Exception {
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", "x".repeat(129)), 400);
        call(post("/api/v1/auth/logout"), Map.of("refreshToken", "x".repeat(129)), 400);
    }

    @Test
    void passwordsOverBcryptsByteLimitAreRejectedWithA400() throws Exception {
        String twoByteChars = "ğ".repeat(40);
        assertThat(twoByteChars.length()).isLessThanOrEqualTo(72);
        assertThat(twoByteChars.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeGreaterThan(72);

        call(post("/api/v1/auth/register"), Map.of("email", "bytes-" + UUID.randomUUID() + "@example.com", "password", twoByteChars), 400);
        call(post("/api/v1/auth/login"), Map.of("email", "nobody@example.com", "password", twoByteChars), 400);
    }
}
