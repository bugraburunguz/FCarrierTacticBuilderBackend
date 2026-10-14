package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-8: v3 RoleFit yanıtı anlatım (narrative) ve tailored taşır; "Biçilmiş kaftan" yalnız levelFit >= 85; v1 yanıtı kaftan iddiasında bulunmaz. */
@IntegrationTest
class RoleFitNarrativeIntegrationTest {

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
    @Autowired
    private JdbcTemplate jdbc;

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode fit(String token, long playerId, String roleId, String version) throws Exception {
        return support.json(post("/api/v1/fit/role").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("playerId", playerId, "roleId", roleId, "position", "ST", "engineVersion", version))), token)
                .at("/data/roleFit");
    }

    @Test
    void v3ResponsesCarryANarrativeWhoseTailoredFlagFollowsTheLevelFitThreshold() throws Exception {
        String token = support.register();
        List<Long> ids = jdbc.queryForList("SELECT base_player_id FROM player_rating ORDER BY base_player_id LIMIT 40", Long.class);
        int seen = 0;
        for (Long id : ids) {
            JsonNode r = fit(token, id, "advanced_forward_attack", "v3");
            if (!r.path("narrative").isObject()) {
                continue;
            }
            seen++;
            boolean tailored = r.path("narrative").path("tailored").asBoolean();
            if (r.hasNonNull("levelFit")) {
                assertThat(tailored).as("player " + id + " levelFit " + r.path("levelFit")).isEqualTo(r.path("levelFit").asDouble() >= 85.0);
            } else {
                assertThat(tailored).isFalse();
            }
            assertThat(r.path("narrative").path("headline").asText()).isNotBlank();
            if (!tailored) {
                assertThat(r.toString()).doesNotContain("kaftan");
            }
            r.path("narrative").path("strengths").forEach((s) -> assertThat(s.path("text").asText()).isNotBlank());
        }
        assertThat(seen).as("v3 yanıtı narrative içeriyor").isEqualTo(ids.size());
    }

    @Test
    void theV1EngineNeverClaimsATailoredFit() throws Exception {
        String token = support.register();
        List<Long> ids = jdbc.queryForList("SELECT base_player_id FROM player_rating ORDER BY base_player_id LIMIT 40", Long.class);
        for (Long id : ids) {
            assertThat(fit(token, id, "advanced_forward_attack", "v1").toString()).as("v1 player " + id).doesNotContainIgnoringCase("kaftan");
        }
    }

    /** Oyuncu sayfası (rol listesi) da öneriler ve RoleFit ile AYNI anlatımı taşır: aynı levelFit eşiği, aynı cümle kalıbı. */
    @Test
    void thePlayerPageRolesCarryTheSameNarrativeAndTheSameTailoredRule() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Long playerId = jdbc.queryForObject("SELECT base_player_id FROM player_rating ORDER BY overall DESC, base_player_id LIMIT 1", Long.class);
        JsonNode roles = support.json(get("/api/v1/players/" + playerId + "/roles?career=" + careerId), token).at("/data");
        assertThat(roles.isArray() && roles.size() > 0).isTrue();
        for (JsonNode r : roles) {
            assertThat(r.path("narrative").isObject()).as(r.path("roleId").asText()).isTrue();
            boolean tailored = r.path("narrative").path("tailored").asBoolean();
            assertThat(tailored).isEqualTo(r.hasNonNull("levelFit") && r.path("levelFit").asDouble() >= 85.0);
            assertThat(r.path("fitLabel").asText()).isNotBlank();
            if (!tailored) {
                assertThat(r.path("narrative").toString()).doesNotContain("kaftan");
            }
        }
    }

    /** V3-05b: UT oyuncu sayfası rolleri `engine=v3` ile v3 (currentFit + levelFit + fitLabel + narrative) döner; parametresiz uç eski davranışı korur. */
    @Test
    void thePlayerRolesEndpointServesV3WhenAskedAndKeepsTheDefaultOtherwise() throws Exception {
        long id = jdbc.queryForList("SELECT base_player_id FROM player_rating ORDER BY base_player_id LIMIT 1", Long.class).get(0);
        JsonNode v3 = objectMapper.readTree(mockMvc.perform(get("/api/v1/players/" + id + "/roles").param("engine", "v3")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(v3).isNotEmpty();
        v3.forEach((r) -> {
            assertThat(r.path("engineVersion").asText()).isEqualTo("v3");
            assertThat(r.path("currentFit").isNumber()).isTrue();
            assertThat(r.path("narrative").isObject()).isTrue();
            if (r.hasNonNull("levelFit")) {
                assertThat(r.path("fitLabel").asText()).isIn("GOOD", "AT_LEVEL", "WEAK", "WRONG_ROLE");
            }
        });
        JsonNode plain = objectMapper.readTree(mockMvc.perform(get("/api/v1/players/" + id + "/roles")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(plain.get(0).path("engineVersion").asText()).isNotEqualTo("");
        mockMvc.perform(get("/api/v1/players/" + id + "/roles").param("engine", "v9")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
