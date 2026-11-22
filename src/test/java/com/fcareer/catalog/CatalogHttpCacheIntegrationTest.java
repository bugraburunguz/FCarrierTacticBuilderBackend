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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
class CatalogHttpCacheIntegrationTest {

    private static final List<String> CACHED_PATHS = List.of("/api/v1/formations", "/api/v1/presets", "/api/v1/roles", "/api/v1/behavior-tags",
            "/api/v1/config/key-attributes", "/api/v1/leagues", "/api/v1/nationalities");

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
    void staticCatalogEndpointsArePubliclyCacheableAndRevalidateWithAnEtag() throws Exception {
        for (String path : CACHED_PATHS) {
            MockHttpServletResponse first = mockMvc.perform(get(path)).andReturn().getResponse();
            assertThat(first.getStatus()).as(path).isEqualTo(200);
            assertThat(first.getHeader("Cache-Control")).as(path).contains("public").contains("max-age=300").doesNotContain("no-store");
            String etag = first.getHeader("ETag");
            assertThat(etag).as(path).isNotBlank();

            MockHttpServletResponse revalidated = mockMvc.perform(get(path).header("If-None-Match", etag)).andReturn().getResponse();
            assertThat(revalidated.getStatus()).as(path).isEqualTo(304);
        }
    }

    @Test
    void aSinglePresetIsCacheableButAMissingOneIsNot() throws Exception {
        JsonNode presets = objectMapper.readTree(mockMvc.perform(get("/api/v1/presets")).andReturn().getResponse().getContentAsString()).at("/data");
        String id = presets.get(0).path("id").asText();

        MockHttpServletResponse found = mockMvc.perform(get("/api/v1/presets/" + id)).andReturn().getResponse();
        MockHttpServletResponse missing = mockMvc.perform(get("/api/v1/presets/no-such-preset")).andReturn().getResponse();

        assertThat(found.getHeader("Cache-Control")).contains("public");
        assertThat(found.getHeader("ETag")).isNotBlank();
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(missing.getHeader("Cache-Control")).contains("no-store").doesNotContain("public");
        assertThat(missing.getHeader("ETag")).isNull();
    }

    @Test
    void otherEndpointsKeepTheNoStoreDefault() throws Exception {
        MockHttpServletResponse clubs = mockMvc.perform(get("/api/v1/clubs")).andReturn().getResponse();

        assertThat(clubs.getHeader("Cache-Control")).contains("no-store");
        assertThat(clubs.getHeader("ETag")).isNull();
    }

    @Test
    void nationalitiesAreServedFromCacheUntilACatalogWriteEvictsThem() throws Exception {
        long before = totalPlayers();
        Integer nationality = jdbc.queryForObject("SELECT id FROM nationality ORDER BY id LIMIT 1", Integer.class);
        Long added = jdbc.queryForObject("INSERT INTO base_player (full_name, nationality_id) VALUES ('Cache Probe', ?) RETURNING id", Long.class, nationality);
        try {
            assertThat(totalPlayers()).as("önbellekten").isEqualTo(before);

            ingestionService.run();

            assertThat(totalPlayers()).as("içe aktarma önbelleği boşaltır").isEqualTo(before + 1);
        } finally {
            jdbc.update("DELETE FROM base_player WHERE id = ?", added);
            ingestionService.run();
        }
    }

    private long totalPlayers() throws Exception {
        JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/v1/nationalities")).andReturn().getResponse().getContentAsString()).at("/data");
        long total = 0;
        for (JsonNode item : items) {
            total += item.path("count").asLong();
        }
        return total;
    }
}
