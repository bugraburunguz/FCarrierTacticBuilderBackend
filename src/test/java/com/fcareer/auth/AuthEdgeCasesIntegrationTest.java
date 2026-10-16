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
        String firstRefresh = call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!", "privacyVersion", 1), 200).at("/data/refreshToken").asText();
        String secondRefresh = call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), 200).at("/data/refreshToken").asText();

        call(post("/api/v1/auth/logout"), Map.of("refreshToken", firstRefresh), 200);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", firstRefresh), 401);

        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", secondRefresh), 200);
    }

    @Test
    void logoutRevokesTheUsersLinkTokens() throws Exception {
        String email = "link-" + UUID.randomUUID() + "@example.com";
        JsonNode session = call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!", "privacyVersion", 1), 200).at("/data");
        var linkResponse = mockMvc.perform(post("/api/v1/ut/capture/link").header("Authorization", "Bearer " + session.at("/accessToken").asText())).andReturn().getResponse();
        String linkToken = objectMapper.readTree(linkResponse.getContentAsString()).at("/data/token").asText();
        var capture = post("/api/v1/ut/capture").header("X-Link-Token", linkToken).contentType(MediaType.APPLICATION_JSON).content("{\"captures\":[]}");
        assertThat(mockMvc.perform(capture).andReturn().getResponse().getStatus()).isEqualTo(200);

        call(post("/api/v1/auth/logout"), Map.of("refreshToken", session.at("/refreshToken").asText()), 200);

        var again = post("/api/v1/ut/capture").header("X-Link-Token", linkToken).contentType(MediaType.APPLICATION_JSON).content("{\"captures\":[]}");
        assertThat(mockMvc.perform(again).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    /** V3-07a profil: şifre değiştir — mevcut şifre doğrulanır (yanlışsa 422, oturum düşmez), yeni şifre kayıt kurallarıyla, diğer oturumlar kapanır. */
    @Test
    void changingThePasswordChecksTheCurrentOneAndRevokesOtherSessions() throws Exception {
        String email = "pw-" + UUID.randomUUID() + "@example.com";
        JsonNode session = call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!", "privacyVersion", 1), 200).at("/data");
        String otherRefresh = call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), 200).at("/data/refreshToken").asText();
        String bearer = "Bearer " + session.at("/accessToken").asText();

        call(post("/api/v1/me/password").header("Authorization", bearer), Map.of("currentPassword", "yanlis-sifre", "newPassword", "Yen1Sifre!!"), 422);
        call(post("/api/v1/me/password").header("Authorization", bearer), Map.of("currentPassword", "Sup3rSecret!", "newPassword", "kisa"), 400);
        call(post("/api/v1/me/password"), Map.of("currentPassword", "Sup3rSecret!", "newPassword", "Yen1Sifre!!"), 401);
        call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), 200);

        JsonNode fresh = call(post("/api/v1/me/password").header("Authorization", bearer), Map.of("currentPassword", "Sup3rSecret!", "newPassword", "Yen1Sifre!!"), 200).at("/data");
        assertThat(fresh.at("/accessToken").asText()).isNotBlank();
        call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), 401);
        call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Yen1Sifre!!"), 200);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", otherRefresh), 401);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", fresh.at("/refreshToken").asText()), 200);
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
