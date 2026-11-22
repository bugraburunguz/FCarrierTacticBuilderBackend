package com.fcareer.catalog.squad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FCA-27 AC-3: GET /api/v1/squad-versions varsayılan oyunun sürümlerini en yeni önce, en yeni işaretli döner; oturum ister. */
@IntegrationTest
class SquadVersionControllerIntegrationTest {

    private static final short DEFAULT_GAME = 27;
    private static final Instant EXPORTED_AT = Instant.parse("2026-11-22T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SquadVersionRepository squadVersions;

    @Test
    @Transactional
    void listsDefaultGameVersionsNewestFirstWithLatestFlag() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        String token = support.register();
        int before = squadVersions.findAllByGameVersion(DEFAULT_GAME).size();
        SquadVersion middle = squadVersions.create(DEFAULT_GAME, "Ara", null, null, 5);
        SquadVersion newest = squadVersions.create(DEFAULT_GAME, "Kadro güncellemesi 22.11.2026 (1.0.142.20665)", "1.0.142.20665", EXPORTED_AT, 22154);

        JsonNode data = support.json(get("/api/v1/squad-versions"), token).at("/data");

        assertThat(data).hasSize(before + 2);
        JsonNode first = data.get(0);
        assertThat(first.get("id").asLong()).isEqualTo(newest.id());
        assertThat(first.get("versionNo").asInt()).isEqualTo(newest.versionNo());
        assertThat(first.get("label").asText()).isEqualTo(newest.label());
        assertThat(first.get("gameBuild").asText()).isEqualTo("1.0.142.20665");
        assertThat(Instant.parse(first.get("exportedAt").asText())).isEqualTo(EXPORTED_AT);
        assertThat(first.get("playerCount").asInt()).isEqualTo(22154);
        assertThat(first.get("latest").asBoolean()).isTrue();
        assertThat(data.get(1).get("id").asLong()).isEqualTo(middle.id());
        for (int i = 1; i < data.size(); i++) {
            assertThat(data.get(i).get("latest").asBoolean()).isFalse();
            assertThat(data.get(i).get("versionNo").asInt()).isLessThan(data.get(i - 1).get("versionNo").asInt());
        }
        JsonNode versionOne = data.get(data.size() - 1);
        assertThat(versionOne.get("versionNo").asInt()).isEqualTo(1);
        assertThat(versionOne.has("gameBuild")).isTrue();
        assertThat(versionOne.get("gameBuild").isNull()).isTrue();
        assertThat(versionOne.get("exportedAt").isNull()).isTrue();
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/squad-versions")).andExpect(status().isUnauthorized());
    }
}
