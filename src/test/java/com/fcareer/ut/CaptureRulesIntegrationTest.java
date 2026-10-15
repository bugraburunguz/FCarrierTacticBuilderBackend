package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HAR-01: yakalama kuralları tek kaynak (uzantı ve web HAR ayrıştırıcısı aynı uçtan okur). Kişisel mod eşleşmesi (personalMatch) ve
 * uzantıdaki personalOnly girdileri sunucu kurallarında olmalı; yoksa istemciler kendi gömülü listelerine düşer ve kaynak ikiye ayrılır.
 */
@IntegrationTest
class CaptureRulesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rulesCarryPersonalMatchAndFullPersonalOnlyList() throws Exception {
        String body = mockMvc.perform(get("/api/v1/ut/capture/rules")).andReturn().getResponse().getContentAsString();
        JsonNode rules = objectMapper.readTree(body).path("data");

        assertThat(rules.path("personalMatch").isArray()).isTrue();
        assertThat(rules.path("personalMatch").toString()).contains("/club", "/sbs/", "objective", "/transfermarket", "/purchased/items");
        assertThat(rules.path("personalOnly").toString()).contains("/purchased/items");
        assertThat(rules.path("deny").toString()).contains("/auth", "sid=");
        assertThat(rules.path("contrib").path("stripFields").toString()).contains("sellerName");
    }
}
