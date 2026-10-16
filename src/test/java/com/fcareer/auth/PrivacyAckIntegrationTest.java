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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** PROD-PREP 3: kayıt aydınlatma metni sürümünü zorunlu ister; onay zamanı + sürümü saklanır ve /me'de döner; bant onayı sürümü günceller. */
@IntegrationTest
class PrivacyAckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getContentAsString());
    }

    private static String email() {
        return "privacy-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registrationWithoutPrivacyVersionIsRejected() throws Exception {
        call(post("/api/v1/auth/register"), Map.of("email", email(), "password", "Sup3rSecret!"), null, 400);
        call(post("/api/v1/auth/register"), Map.of("email", email(), "password", "Sup3rSecret!", "privacyVersion", 0), null, 400);
    }

    @Test
    void registrationStoresAcknowledgedVersionAndTime() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", email(), "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        JsonNode me = call(get("/api/v1/me"), null, token, 200).at("/data");
        assertThat(me.at("/privacyAckVersion").asInt()).isEqualTo(1);
        assertThat(me.at("/privacyAckAt").asText()).isNotBlank();
    }

    @Test
    void acknowledgingANewerVersionUpdatesTheRecord() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", email(), "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        call(post("/api/v1/me/privacy-ack"), Map.of("version", 2), token, 200);
        assertThat(call(get("/api/v1/me"), null, token, 200).at("/data/privacyAckVersion").asInt()).isEqualTo(2);
        call(post("/api/v1/me/privacy-ack"), Map.of("version", 0), token, 400);
        call(post("/api/v1/me/privacy-ack"), Map.of("version", 2), null, 401);
    }
}
