package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** FCA-27 AC-4: kariyer açılırken kadro sürümü seçilir (yoksa en yeni, bilinmeyen 400), yanıtta squadVersion döner, PATCH sürümü değiştirmez. */
@IntegrationTest
@Transactional
class CareerControllerIntegrationTest {

    private static final short DEFAULT_GAME = 27;
    private static final short OTHER_GAME = 98;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    private CareerTestSupport support;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        token = support.register();
    }

    @Test
    void createWithoutSquadVersionBindsTheLatest() throws Exception {
        SquadVersion newest = squadVersions.create(DEFAULT_GAME, "Yeni", "1.0.142.20665", null, 3);

        JsonNode career = createCareer(null);

        assertThat(career.at("/success").asBoolean()).as(career.toString()).isTrue();
        assertThat(career.at("/data/squadVersion/id").asLong()).isEqualTo(newest.id());
        assertThat(career.at("/data/squadVersion/versionNo").asInt()).isEqualTo(newest.versionNo());
        assertThat(career.at("/data/squadVersion/label").asText()).isEqualTo("Yeni");
        assertThat(career.at("/data/squadVersion/latest").asBoolean()).isTrue();
        assertThat(boundVersion(career.at("/data/id").asLong())).isEqualTo(newest.id());
    }

    @Test
    void createWithOlderSquadVersionBindsItAndResponsesCarryIt() throws Exception {
        SquadVersion older = squadVersions.findLatest(DEFAULT_GAME).orElseThrow();
        squadVersions.create(DEFAULT_GAME, "Yeni", null, null, 3);

        JsonNode created = createCareer(older.id());
        long careerId = created.at("/data/id").asLong();

        assertThat(created.at("/data/squadVersion/id").asLong()).as(created.toString()).isEqualTo(older.id());
        assertThat(created.at("/data/squadVersion/latest").asBoolean()).isFalse();
        assertThat(boundVersion(careerId)).isEqualTo(older.id());

        JsonNode single = support.json(get("/api/v1/careers/" + careerId), token);
        assertThat(single.at("/data/squadVersion/id").asLong()).isEqualTo(older.id());
        assertThat(single.at("/data/squadVersion/label").asText()).isEqualTo(older.label());
        assertThat(single.at("/data/squadVersion/latest").asBoolean()).isFalse();

        JsonNode list = support.json(get("/api/v1/careers"), token).at("/data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).at("/squadVersion/id").asLong()).isEqualTo(older.id());
        assertThat(list.get(0).at("/squadVersion/versionNo").asInt()).isEqualTo(older.versionNo());
    }

    @Test
    void unknownSquadVersionIsBadRequest() throws Exception {
        assertThat(createStatus(Long.MAX_VALUE)).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career WHERE name = 'Sürüm testi'", Integer.class)).isZero();
    }

    @Test
    void squadVersionOfAnotherGameIsBadRequest() throws Exception {
        jdbc.update("INSERT INTO game_version (id, code) VALUES (?, 'TEST98')", OTHER_GAME);
        SquadVersion foreign = squadVersions.create(OTHER_GAME, "Başka oyun", null, null, 0);

        assertThat(createStatus(foreign.id())).isEqualTo(400);
    }

    @Test
    void patchDoesNotChangeTheSquadVersion() throws Exception {
        SquadVersion older = squadVersions.findLatest(DEFAULT_GAME).orElseThrow();
        SquadVersion newest = squadVersions.create(DEFAULT_GAME, "Yeni", null, null, 3);
        long careerId = createCareer(older.id()).at("/data/id").asLong();

        JsonNode patched = support.json(json(patch("/api/v1/careers/" + careerId), Map.of("name", "Yeni ad", "squadVersionId", newest.id())), token);

        assertThat(patched.at("/data/name").asText()).as(patched.toString()).isEqualTo("Yeni ad");
        assertThat(patched.at("/data/squadVersion/id").asLong()).isEqualTo(older.id());
        assertThat(boundVersion(careerId)).isEqualTo(older.id());
    }

    private JsonNode createCareer(Long squadVersionId) throws Exception {
        return support.json(json(post("/api/v1/careers"), body(squadVersionId)), token);
    }

    private int createStatus(Long squadVersionId) throws Exception {
        return mockMvc.perform(json(post("/api/v1/careers"), body(squadVersionId)).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private Map<String, Object> body(Long squadVersionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("clubId", CareerTestSupport.catalogClubId(jdbc));
        body.put("name", "Sürüm testi");
        if (squadVersionId != null) {
            body.put("squadVersionId", squadVersionId);
        }
        return body;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
    }

    private long boundVersion(long careerId) {
        return jdbc.queryForObject("SELECT squad_version_id FROM career WHERE id = ?", Long.class, careerId);
    }
}
