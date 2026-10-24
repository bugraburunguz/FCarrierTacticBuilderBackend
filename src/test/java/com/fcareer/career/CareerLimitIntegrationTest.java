package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * GUV-2 (F4): her kariyer katalog kopyalar; kullanıcı başına açık kariyer üst sınırı (422, "bir kariyeri sil") ve sil-aç döngüsünü
 * kesen günlük açma sınırı (429). Testte sınırlar küçük: en çok 3 kariyer, günde 4 açma.
 */
@IntegrationTest
@TestPropertySource(properties = {"fcareer.ratelimit.career.max-per-user=3", "fcareer.ratelimit.career.daily-create-per-user=4"})
class CareerLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void capOfOpenCareersThenDailyCreateLimit() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "limit-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        long clubId = clubRepository.findAll().get(0).getId();
        Map<String, Object> body = Map.of("clubId", clubId);

        long first = call(post("/api/v1/careers"), body, token, 200).at("/data/id").asLong();
        call(post("/api/v1/careers"), body, token, 200);
        call(post("/api/v1/careers"), body, token, 200);

        JsonNode capped = call(post("/api/v1/careers"), body, token, 422);
        assertThat(capped.at("/error/0/code").asText()).isEqualTo("fc.exception.0031");
        assertThat(capped.toString()).contains("kariyerini sil");

        call(delete("/api/v1/careers/" + first), null, token, 200);
        long fourth = call(post("/api/v1/careers"), body, token, 200).at("/data/id").asLong();

        call(delete("/api/v1/careers/" + fourth), null, token, 200);
        JsonNode daily = call(post("/api/v1/careers"), body, token, 429);
        assertThat(daily.at("/error/0/code").asText()).isEqualTo("fc.exception.0032");
    }

    /** Geçersiz istek (yok kulüp, 404) günlük kotayı tüketmez; aksi hâlde kullanıcı kendi gününü kilitlerdi. */
    @Test
    void failedCreatesDoNotUseTheDailyQuota() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "limit-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        for (int i = 0; i < 5; i++) {
            call(post("/api/v1/careers"), Map.of("clubId", 999_999_999), token, 404);
        }
        long clubId = clubRepository.findAll().get(0).getId();
        for (int i = 0; i < 3; i++) {
            call(post("/api/v1/careers"), Map.of("clubId", clubId), token, 200);
        }
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
        String content = result.getContentAsString();
        return content.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
