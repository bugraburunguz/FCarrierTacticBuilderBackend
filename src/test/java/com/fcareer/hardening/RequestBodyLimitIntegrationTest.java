package com.fcareer.hardening;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@TestPropertySource(properties = {"fcareer.limits.json-bytes=2048", "fcareer.limits.capture-bytes=100000", "fcareer.limits.multipart-bytes=50000"})
class RequestBodyLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token() throws Exception {
        String body = "{\"email\":\"lim-" + UUID.randomUUID() + "@example.com\",\"password\":\"Sup3rSecret!\",\"privacyVersion\":1}";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/accessToken").asText();
    }

    private static String json(int bytes) {
        return "{\"captures\":[{\"url\":\"https://x/y\",\"ts\":1,\"body\":\"" + "x".repeat(bytes) + "\"}]}";
    }

    private int status(String path, String body, boolean chunked, String bearer) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + bearer).content(body.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void chunkedBodyIsStoppedWhileReadingNotOnlyByContentLength() throws Exception {
        String bearer = token();

        assertThat(status("/api/v1/ut/capture", json(150_000), true, bearer)).isEqualTo(413);
        assertThat(status("/api/v1/ut/capture", json(150_000), false, bearer)).isEqualTo(413);
        assertThat(status("/api/v1/ut/capture", json(20_000), true, bearer)).isEqualTo(200);
    }

    @Test
    void contribEndpointAndOrdinaryJsonEndpointsHaveTheirOwnBudgets() throws Exception {
        String bearer = token();

        assertThat(status("/api/v1/ut/contrib/capture", json(150_000), true, bearer)).isEqualTo(413);
        assertThat(status("/api/v1/auth/login", "{\"email\":\"" + "a".repeat(5_000) + "\",\"password\":\"x\"}", true, bearer)).isEqualTo(413);
    }

}
