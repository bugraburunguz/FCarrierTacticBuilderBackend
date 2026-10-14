package com.fcareer.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** REP-04: eski 34 REPLICA preset'i yeni 112 replikaya taşındı; eski kayıtlar presets.json'dan ve `preset` tablosundan çıktı; eski uç bir sürüm Deprecation ile yaşar. */
@IntegrationTest
class ReplicaMigrationIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int LEGACY_REPLICAS = 34;
    private static final int MAPPED = 33;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReplicaService replicas;

    private static JsonNode seed(String file) throws Exception {
        try (InputStream in = new ClassPathResource("seed/" + file).getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    @Test
    void theMappingCoversAll34OldReplicasAndEveryTargetExists() throws Exception {
        JsonNode map = seed("replica-legacy-ids.json");
        assertThat(map).hasSize(LEGACY_REPLICAS);
        Set<String> targets = new HashSet<>(replicas.all().stream().map(ReplicaService.Replica::id).toList());
        int mapped = 0;
        for (JsonNode row : map) {
            if (row.path("to").isNull()) {
                assertThat(row.path("reason").asText()).as(row.path("from").asText() + " eşleşmeyen: gerekçe").isNotBlank();
                continue;
            }
            mapped++;
            assertThat(targets).as(row.path("from").asText() + " hedefi").contains(row.path("to").asText());
        }
        assertThat(mapped).isEqualTo(MAPPED);
    }

    @Test
    void oldReplicaRecordsAreGoneFromTheSeedAndTheTableButOtherPresetsStay() throws Exception {
        for (JsonNode p : seed("presets.json")) {
            assertThat(p.path("kind").asText()).as(p.path("id").asText()).isNotEqualTo("REPLICA");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preset WHERE kind = 'REPLICA'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preset WHERE kind IN ('STYLE','PERSONAL')", Integer.class)).isEqualTo(10);
    }

    @Test
    void legacyIdEndpointServesTheMapping() throws Exception {
        String body = mockMvc.perform(get("/api/v1/replicas/legacy-ids")).andReturn().getResponse().getContentAsString();
        JsonNode data = MAPPER.readTree(body).at("/data");
        assertThat(data.path("pep_barcelona_2011").asText()).isEqualTo("guardiola_barcelona_2011");
        assertThat(data.has("pulis_stoke_2011")).isTrue();
        assertThat(data.path("pulis_stoke_2011").isNull()).isTrue();
    }

    @Test
    void theOldReplicaPresetEndpointStillAnswersWithADeprecationHeaderPointingAtReplicas() throws Exception {
        var res = mockMvc.perform(get("/api/v1/presets").param("kind", "REPLICA")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(MAPPER.readTree(res.getContentAsString()).at("/data")).isEmpty();
        assertThat(res.getHeader("Deprecation")).isEqualTo("true");
        assertThat(res.getHeader("Link")).contains("/api/v1/replicas").contains("successor-version");
        var other = mockMvc.perform(get("/api/v1/presets").param("kind", "STYLE")).andReturn().getResponse();
        assertThat(other.getHeader("Deprecation")).isNull();
    }
}
