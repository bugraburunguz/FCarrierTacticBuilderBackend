package com.fcareer.billing;

import com.fcareer.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@TestPropertySource(properties = "fcareer.billing.stub-enabled=false")
class NoPaymentProviderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void subscribeNeverGrantsPremiumWithoutAProvider() throws Exception {
        var register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", "user-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1)))).andReturn().getResponse();
        String token = objectMapper.readTree(register.getContentAsString()).at("/data/accessToken").asText();

        var response = mockMvc.perform(post("/api/v1/billing/subscribe").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(objectMapper.readTree(response.getContentAsString()).at("/error/0/code").asText()).isEqualTo("fc.exception.0018");

        JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertThat(me.at("/data/subscriptionType").asText()).isNotEqualTo("PREMIUM");
    }
}
