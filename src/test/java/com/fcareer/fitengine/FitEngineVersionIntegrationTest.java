package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.fitengine.level.FitLevelNormsBuilder;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-5: engine_version yapılandırması ve /fit/role engineVersion geçersiz kılması (v1 varsayılan; v3 currentFit + levelFit + katkılar). */
@IntegrationTest
class FitEngineVersionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private FitLevelNormsBuilder levelNormsBuilder;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, Object body, int status) throws Exception {
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        String json = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private long playerId() throws Exception {
        return call(get("/api/v1/players?size=1"), null, 200).at("/data/items/0/id").asLong();
    }

    @Test
    void roleFitDefaultsToV1AndKeepsItsShape() throws Exception {
        JsonNode fit = call(post("/api/v1/fit/role"), Map.of("playerId", playerId(), "roleId", "winger_attack", "position", "RW"), 200);
        assertThat(fit.at("/data/roleFit/engineVersion").asText()).isEqualTo("v1");
        assertThat(fit.at("/data/roleFit/currentFit").isMissingNode()).isTrue();
        assertThat(fit.at("/data/roleFit/weaponChecks").isArray()).isTrue();
    }

    @Test
    void roleFitWithEngineVersionV3ReturnsCurrentFitLevelFitAndTopContributions() throws Exception {
        levelNormsBuilder.rebuild((short) 27);
        JsonNode fit = call(post("/api/v1/fit/role"), Map.of("playerId", playerId(), "roleId", "winger_attack", "position", "RW", "engineVersion", "v3"), 200);
        JsonNode roleFit = fit.at("/data/roleFit");
        assertThat(roleFit.at("/engineVersion").asText()).isEqualTo("v3");
        assertThat(roleFit.at("/currentFit").asDouble()).isBetween(0.0, 100.0);
        assertThat(roleFit.at("/score").asDouble()).isEqualTo(roleFit.at("/currentFit").asDouble());
        assertThat(roleFit.at("/levelFit").asDouble()).isBetween(0.0, 100.0);
        assertThat(roleFit.at("/contributions").size()).isBetween(1, 3);
    }

    @Test
    void anUnknownEngineVersionIsRejected() throws Exception {
        JsonNode fit = call(post("/api/v1/fit/role"), Map.of("playerId", playerId(), "roleId", "winger_attack", "engineVersion", "v9"), 400);
        assertThat(fit.at("/success").asBoolean()).isFalse();
    }
}
