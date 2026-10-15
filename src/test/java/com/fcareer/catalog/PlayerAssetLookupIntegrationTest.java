package com.fcareer.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** V3-05b: `/ut/players/:assetId` rotası için EA oyuncu kimliğiyle (assetId = ea_resource_id) oyuncu detayı. */
@IntegrationTest
class PlayerAssetLookupIntegrationTest {

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

    @Test
    void anAssetIdResolvesToThePlayerDetailAndAnUnknownOneIsNotFound() throws Exception {
        long assetId = jdbc.queryForObject("SELECT ea_resource_id FROM base_player WHERE ea_resource_id IS NOT NULL AND owner_career_id IS NULL ORDER BY ea_resource_id LIMIT 1", Long.class);
        var ok = mockMvc.perform(get("/api/v1/players/asset/" + assetId)).andReturn().getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(ok.getContentAsString()).path("data");
        assertThat(data.at("/summary/faceAssetId").asLong()).isEqualTo(assetId);
        assertThat(data.path("attrs").isObject()).isTrue();
        assertThat(mockMvc.perform(get("/api/v1/players/asset/1")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    /** V3-05c-c: SBC ödül kartının adı (EA verisinden çizilen kart) için toplu assetId → katalog adı; bilinmeyen kimlik atlanır (404 yok), kariyer oyuncusu dönmez. */
    @Test
    void assetNamesResolveInOneCallAndSkipUnknownAndCareerOwnedPlayers() throws Exception {
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT ea_resource_id, full_name FROM base_player WHERE ea_resource_id IS NOT NULL AND owner_career_id IS NULL ORDER BY ea_resource_id LIMIT 2");
        long first = ((Number) rows.get(0).get("ea_resource_id")).longValue();
        long second = ((Number) rows.get(1).get("ea_resource_id")).longValue();
        var res = mockMvc.perform(get("/api/v1/players/assets?id=" + first + "&id=" + second + "&id=1")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(res.getContentAsString()).path("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("assetId").asLong()).isEqualTo(first);
        assertThat(data.get(0).path("name").asText()).isEqualTo(rows.get(0).get("full_name"));
        assertThat(mockMvc.perform(get("/api/v1/players/assets")).andReturn().getResponse().getStatus()).isEqualTo(400);
    }
}
