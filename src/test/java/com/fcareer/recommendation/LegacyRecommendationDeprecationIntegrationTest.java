package com.fcareer.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-10: web artık eski öneri uçlarını çağırmaz; uçlar bir sürüm `Deprecation` başlığıyla yaşar. Maliyetler UI'da açık olsun diye ayrı uçtan okunur. */
@IntegrationTest
class LegacyRecommendationDeprecationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
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
    void theLegacyRecommendationEndpointsCarryADeprecationHeaderPointingAtTheNewOne() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        for (String path : new String[]{"/api/v1/recommend/slot-view", "/api/v1/recommend/slot", "/api/v1/recommend/transfer-targets", "/api/v1/scouting/" + careerId + "/search"}) {
            MvcResult result = call(post(path), token);
            assertThat(result.getResponse().getHeader("Deprecation")).as(path).isEqualTo("true");
            assertThat(result.getResponse().getHeader("Link")).as(path).contains("/recommendations").contains("successor-version");
        }
        assertThat(call(post("/api/v1/careers/" + careerId + "/recommendations"), token).getResponse().getHeader("Deprecation")).as("yeni uç").isNull();
    }

    @Test
    void theCreditCostsAreReadableSoTheUiCanSayHowManyCreditsWillBeSpent() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        String token = support.register();
        JsonNode costs = support.json(get("/api/v1/billing/costs"), token);
        assertThat(costs.at("/data/recommend").asInt()).isPositive();
        assertThat(costs.at("/data/squadFit").asInt()).isPositive();
        assertThat(costs.at("/data/advisor").asInt()).isPositive();
    }
}
