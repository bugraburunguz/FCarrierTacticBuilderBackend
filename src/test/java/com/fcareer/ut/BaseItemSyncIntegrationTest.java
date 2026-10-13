package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class BaseItemSyncIntegrationTest {

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
        jdbc.update("DELETE FROM ut_item WHERE source = 'EA_SITE'");
    }

    private void sync() throws Exception {
        call(post("/api/v1/admin/ut/items/sync-base").header("X-Admin-Key", "test-key"), null, 200);
    }

    @Test
    void baseItemsCarryEaIdsQualityAndNoInventedRarity() throws Exception {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT b.ea_resource_id AS resource, r.club_id AS club, c.league_id AS league, b.nationality_id AS nation
                FROM base_player b JOIN player_rating r ON r.base_player_id = b.id JOIN club c ON c.id = r.club_id
                WHERE b.ea_resource_id IS NOT NULL AND b.owner_career_id IS NULL AND b.nationality_id IS NOT NULL LIMIT 1""");
        jdbc.update("UPDATE club SET ea_team_id = 90001 WHERE id = ?", row.get("club"));
        jdbc.update("UPDATE league SET ea_league_id = 90002 WHERE id = ?", row.get("league"));
        jdbc.update("UPDATE nationality SET ea_nation_id = 90003 WHERE id = ?", row.get("nation"));

        sync();

        Map<String, Object> item = jdbc.queryForMap("SELECT club_id, league_id, nation_id, is_rare, rating FROM ut_item WHERE ea_resource_id = ?", row.get("resource"));
        assertThat(item.get("club_id")).isEqualTo(90001);
        assertThat(item.get("league_id")).isEqualTo(90002);
        assertThat(item.get("nation_id")).isEqualTo(90003);
        assertThat(item.get("is_rare")).isEqualTo(false);

        JsonNode dto = call(get("/api/v1/ut/kb/items?limit=50"), null, 200).at("/data");
        dto.forEach(i -> assertThat(i.at("/quality").asText()).isIn("BRONZE", "SILVER", "GOLD"));
    }

    @Test
    void resyncRefreshesNameAndAttributes() throws Exception {
        sync();
        Long id = jdbc.queryForObject("SELECT id FROM ut_item ORDER BY id LIMIT 1", Long.class);
        jdbc.update("UPDATE ut_item SET name = 'ESKI AD', weak_foot = 1, skill_moves = 1, accelerate = 'ESKI' WHERE id = ?", id);

        sync();

        Map<String, Object> item = jdbc.queryForMap("SELECT name, weak_foot, skill_moves, accelerate FROM ut_item WHERE id = ?", id);
        assertThat(item.get("name")).isNotEqualTo("ESKI AD");
        assertThat(item.get("accelerate")).isNotEqualTo("ESKI");
        assertThat(item.get("weak_foot")).isNotEqualTo(1);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getContentAsString());
    }
}
