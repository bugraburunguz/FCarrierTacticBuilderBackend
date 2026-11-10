package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** ENG-02: boş/0 bütçe "bilinmiyor"dur; piyasa boşalmaz, kariyer bütçesi uydurma 0'a çevrilmez. */
@IntegrationTest
class UnknownBudgetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "bud-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
    }

    /** FCA-10: eski `/recommend/transfer-targets` kaldırıldı; kural canlı kariyer öneri ucundan doğrulanır. */
    private JsonNode recommendationsFor(Map<String, Object> careerBody) throws Exception {
        String token = register();
        long careerId = call(post("/api/v1/careers"), careerBody, token, 200).at("/data/id").asLong();
        return call(post("/api/v1/careers/" + careerId + "/recommendations"), Map.of("position", "ST", "roleId", "poacher_attack"), token, 200).at("/data/result");
    }

    @Test
    void aCareerCreatedWithoutABudgetKeepsItUnknownNotZero() throws Exception {
        String token = register();
        JsonNode created = call(post("/api/v1/careers"), Map.of("clubId", CareerTestSupport.catalogClubId(jdbc)), token, 200).at("/data");
        assertThat(created.has("budgetEur") && !created.get("budgetEur").isNull()).as(created.toString()).isFalse();
    }

    @Test
    void noBudgetOrAZeroBudgetDoesNotEmptyTheMarket() throws Exception {
        int clubId = CareerTestSupport.catalogClubId(jdbc);
        // Gerçekçi durum: herkesin bir değeri var; `valueMax=0` hepsini elerdi.
        jdbc.update("INSERT INTO player_valuation (base_player_id, game_version_id, source, value_eur, model_version) SELECT base_player_id, 27, 'MODELED', 2000000, 'eng02-test' FROM player_rating WHERE game_version_id = 27 ON CONFLICT DO NOTHING");
        try {
            assertThat(hasValuedSigning(recommendationsFor(Map.of("clubId", clubId)).at("/items"))).as("bütçe girilmemiş").isTrue();
            assertThat(hasValuedSigning(recommendationsFor(Map.of("clubId", clubId, "budgetEur", 0)).at("/items"))).as("bütçe 0 = bilinmiyor").isTrue();
            assertThat(hasValuedSigning(recommendationsFor(Map.of("clubId", clubId, "budgetEur", 1)).at("/items"))).as("kontrast: bilinen 1 € bütçe değerli adayı eler").isFalse();
        } finally {
            jdbc.update("DELETE FROM player_valuation WHERE model_version = 'eng02-test'");
        }
    }

    /** Yalnız serbest ya da sözleşmesi biten oyuncu dönseydi 0 bütçe piyasayı yine boşaltmış olurdu; değeri olan bir transfer adayı aranır. */
    private static boolean hasValuedSigning(JsonNode items) {
        for (JsonNode item : items) {
            if (!item.at("/player/freeAgent").asBoolean() && item.at("/player/valueEur").asLong() > 0) {
                return true;
            }
        }
        return false;
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
