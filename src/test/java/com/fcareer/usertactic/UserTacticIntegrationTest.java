package com.fcareer.usertactic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.recommendation.pipeline.TacticSlotLookup;
import com.fcareer.replica.ReplicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** REP-07: "Taktik slotuma kaydet": 5 slotlu user_tactic; replikanın kodu ve ayarları kopyalanır, source_replica_id dolar. */
@IntegrationTest
class UserTacticIntegrationTest {

    private static final String REPLICA = "guardiola_barcelona_2011";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private ReplicaService replicas;
    @Autowired
    private TacticSlotLookup slotLookup;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "ut-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
    }

    private long career(String token) throws Exception {
        return call(post("/api/v1/careers"), Map.of("clubId", clubRepository.findAll().get(0).getId(), "budgetEur", 5_000_000), token, 200).at("/data/id").asLong();
    }

    @Test
    void savingAReplicaCopiesItsCodeAndSettingsIntoTheSlot() throws Exception {
        String token = register();
        long careerId = career(token);
        JsonNode saved = call(put("/api/v1/user-tactics/2"), Map.of("careerId", careerId, "replicaId", REPLICA), token, 200).path("data");
        ReplicaService.Replica replica = replicas.find(REPLICA).orElseThrow();
        assertThat(saved.path("slot").asInt()).isEqualTo(2);
        assertThat(saved.path("sourceReplicaId").asText()).isEqualTo(REPLICA);
        assertThat(saved.path("eaCode").asText()).isEqualTo(replica.tacticCode());
        assertThat(saved.path("formation").asText()).isEqualTo(replica.formationId());
        assertThat(saved.path("buildUp").asText()).isEqualTo(replica.buildUp());
        assertThat(saved.path("lineHeight").asInt()).isEqualTo(replica.lineHeight());
        assertThat(saved.path("slots")).hasSize(11);
        assertThat(saved.path("name").asText()).contains("Guardiola");
        assertThat(slotLookup.find(careerId, "ST", "ST")).as("öneri hattı slot rolünü kayıtlı taktikten okur").isPresent();
    }

    @Test
    void savingToTheSameSlotReplacesIt_andListShowsAtMostFive() throws Exception {
        String token = register();
        long careerId = career(token);
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "replicaId", REPLICA), token, 200);
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "replicaId", "sacchi_milan_1989"), token, 200);
        JsonNode list = call(get("/api/v1/user-tactics").param("careerId", String.valueOf(careerId)), null, token, 200).path("data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("sourceReplicaId").asText()).isEqualTo("sacchi_milan_1989");
    }

    @Test
    void utTacticsAreSeparateFromCareerTactics_andCanBeDeleted() throws Exception {
        String token = register();
        long careerId = career(token);
        call(put("/api/v1/user-tactics/1"), Map.of("replicaId", REPLICA), token, 200);
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "replicaId", REPLICA), token, 200);
        assertThat(call(get("/api/v1/user-tactics"), null, token, 200).path("data")).hasSize(1);
        assertThat(call(get("/api/v1/user-tactics").param("careerId", String.valueOf(careerId)), null, token, 200).path("data")).hasSize(1);
        call(delete("/api/v1/user-tactics/1"), null, token, 200);
        assertThat(call(get("/api/v1/user-tactics"), null, token, 200).path("data")).isEmpty();
    }

    @Test
    void invalidSlotUnknownReplicaAndForeignCareerAreRejected() throws Exception {
        String token = register();
        call(put("/api/v1/user-tactics/6"), Map.of("replicaId", REPLICA), token, 400);
        call(put("/api/v1/user-tactics/0"), Map.of("replicaId", REPLICA), token, 400);
        call(put("/api/v1/user-tactics/1"), Map.of("replicaId", "yok_boyle_bir_replika"), token, 404);
        long foreign = career(register());
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", foreign, "replicaId", REPLICA), token, 404);
        call(get("/api/v1/user-tactics").param("careerId", String.valueOf(foreign)), null, token, 404);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var result = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getContentAsString());
    }
}
