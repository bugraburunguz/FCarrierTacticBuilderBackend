package com.fcareer.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * FCA-10 (ESKI-UC, 16 S3): E3-10'da `Deprecation` başlığıyla bir sürüm yaşayan eski öneri uçları kaldırıldı; hiçbir istemci çağırmıyor.
 * Halefleri (`/careers/{id}/recommendations`, `/advisor/tactic`) ve UT önizlemesi (`/recommend/preview`) yaşar. Maliyetler UI'da açık olsun diye ayrı uçtan okunur.
 */
@IntegrationTest
class LegacyRecommendationDeprecationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private MvcResult call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    }

    @Test
    void theRemovedLegacyRecommendationEndpointsAnswer404() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        for (String path : new String[]{"/api/v1/recommend/slot-view", "/api/v1/recommend/slot", "/api/v1/recommend/transfer-targets",
                "/api/v1/scouting/" + careerId + "/search", "/api/v1/advisor/squad"}) {
            MvcResult result = call(post(path), token);
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
            assertThat(result.getResponse().getHeader("Deprecation")).as(path).isNull();
        }
    }

    @Test
    void theSuccessorEndpointsStillExist() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        for (String path : new String[]{"/api/v1/recommend/preview", "/api/v1/advisor/tactic", "/api/v1/careers/" + careerId + "/recommendations"}) {
            MvcResult result = call(post(path), token);
            assertThat(result.getResponse().getStatus()).as(path).isNotEqualTo(404).isLessThan(500);
            assertThat(result.getResponse().getHeader("Deprecation")).as(path).isNull();
        }
    }

    @Test
    void theCreditCostsAreReadableSoTheUiCanSayHowManyCreditsWillBeSpent() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        String token = support.register();
        JsonNode costs = support.json(get("/api/v1/billing/costs"), token);
        assertThat(costs.at("/data/recommend").asInt()).isPositive();
        assertThat(costs.at("/data/squadFit").asInt()).isPositive();
        assertThat(costs.at("/data/advisor").asInt()).isPositive();
    }
}
