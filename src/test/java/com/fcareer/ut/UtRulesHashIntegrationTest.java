package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.ut.service.UtRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
class UtRulesHashIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theRecordedHashMatchesTheRulesDocument() throws Exception {
        String recorded = new ClassPathResource("seed/ut-rules.fc27.hash").getContentAsString(StandardCharsets.UTF_8).trim();

        assertThat(new UtRules(new ObjectMapper()).hash())
                .as("kurallar değişti: seed/ut-rules.fc27.hash ve web src/lib/utRules.fc27.hash.json güncellenmeli (web: npm run sync:rules)")
                .isEqualTo(recorded);
    }

    @Test
    void rulesResponseCarriesTheHashAndAnEtagThatAnswers304() throws Exception {
        var response = mockMvc.perform(get("/api/v1/ut/rules")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        String hash = body.at("/data/rulesHash").asText();
        assertThat(hash).hasSize(64);
        assertThat(response.getHeader("ETag")).isEqualTo("\"" + hash + "\"");
        assertThat(response.getHeader("Cache-Control")).contains("max-age");

        var notModified = mockMvc.perform(get("/api/v1/ut/rules").header("If-None-Match", "\"" + hash + "\"")).andReturn().getResponse();
        assertThat(notModified.getStatus()).isEqualTo(304);
    }
}
