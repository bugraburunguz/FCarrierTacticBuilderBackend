package com.fcareer.advisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.advisor.service.FormationRanker;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.tactics.TacticDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * KARIYER-UX FAZ 1 (D-31): ana taktik yokken ücretsiz otomatik başlangıç önerisi. Formasyon-only sıralamanın (advisor ile paylaşılan) birincisi +
 * varsayılan kurulum + o formasyonla /fit/lineup. Kredi düşmez, preset taraması ve ücretli içgörü yok; sonuç kariyer + son içe aktarma için önbellekte.
 */
@IntegrationTest
class StarterTacticIntegrationTest {

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
    @MockitoSpyBean
    private FormationRanker ranker;

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, String token, int expected) throws Exception {
        var response = mockMvc.perform(builder.accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    void theStarterIsFreeAndGivesTheTopFormationWithItsBestEleven() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        int before = call(get("/api/v1/me"), token, 200).at("/data/creditBalance").asInt();

        JsonNode starter = call(get("/api/v1/careers/" + careerId + "/starter-tactic"), token, 200).at("/data");

        assertThat(call(get("/api/v1/me"), token, 200).at("/data/creditBalance").asInt()).as("kredi düşmez").isEqualTo(before);
        assertThat(starter.at("/formationId").asText()).isNotBlank();
        assertThat(starter.at("/setup/buildUp").asText()).isEqualTo(TacticDefaults.BUILD_UP);
        assertThat(starter.at("/setup/depth").asInt()).isEqualTo(TacticDefaults.DEPTH);
        assertThat(starter.at("/slots")).hasSize(11);
        starter.at("/slots").forEach(s -> assertThat(s.at("/roleId").asText()).isNotBlank());
        assertThat(starter.at("/lineup").size()).isEqualTo(11);
        assertThat(starter.at("/squadFit").isNumber()).isTrue();
        JsonNode alternatives = starter.at("/alternatives");
        assertThat(alternatives).hasSize(2);
        assertThat(alternatives.get(0).at("/formationId").asText()).isNotEqualTo(starter.at("/formationId").asText());
        assertThat(alternatives.get(0).at("/score").asDouble()).isGreaterThanOrEqualTo(alternatives.get(1).at("/score").asDouble());
        assertThat(starter.has("rules")).as("ücretli içgörü yok").isFalse();
        assertThat(starter.has("presetId") && !starter.get("presetId").isNull()).as("preset taraması yok").isFalse();
    }

    @Test
    void ownershipAndSquadSizeAreChecked() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        call(get("/api/v1/careers/" + careerId + "/starter-tactic"), support.register(), 404);

        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        jdbc.update("DELETE FROM user_player_state WHERE career_id = ? AND base_player_id NOT IN (" + String.join(",", squad.subList(0, 10).stream().map(String::valueOf).toList()) + ")",
                careerId);
        call(get("/api/v1/careers/" + careerId + "/starter-tactic"), token, 422);
    }

    @Test
    void theResultIsCachedUntilTheNextImport() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        clearInvocations(ranker);

        JsonNode first = call(get("/api/v1/careers/" + careerId + "/starter-tactic"), token, 200).at("/data");
        call(get("/api/v1/careers/" + careerId + "/starter-tactic"), token, 200);
        verify(ranker, times(1)).rank(any(), any(), any());

        jdbc.update("INSERT INTO career_snapshot (career_id, source, note) VALUES (?, 'IMPORT', 'test')", careerId);
        JsonNode after = call(get("/api/v1/careers/" + careerId + "/starter-tactic"), token, 200).at("/data");
        verify(ranker, times(2)).rank(any(), any(), any());
        assertThat(after.at("/formationId").asText()).isEqualTo(first.at("/formationId").asText());
    }
}
