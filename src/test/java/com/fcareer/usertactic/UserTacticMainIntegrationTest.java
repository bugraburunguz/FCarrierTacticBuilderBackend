package com.fcareer.usertactic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.recommendation.pipeline.TacticSlotLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * KARIYER-UX FAZ 1 (D-30): ana taktik sunucuda. Taslaktan kayıt tek taktik gövdesiyle (TacticRequest: formation, presetId, setup, slots[slotId, roleId, tags])
 * + opsiyonel diziliş; kullanıcı + kariyer başına en fazla bir ana taktik; /main yalnız kariyer için; yoksa data null.
 */
@IntegrationTest
class UserTacticMainIntegrationTest {

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
    @Autowired
    private TacticSlotLookup slotLookup;

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private static Map<String, Object> tactic() {
        Map<String, Object> tactic = new LinkedHashMap<>();
        tactic.put("formation", "4-4-2");
        tactic.put("presetId", null);
        tactic.put("setup", Map.of("buildUp", "Counter", "depth", 35));
        tactic.put("slots", List.of(
                Map.of("slotId", "LCB", "roleId", "defender_defend", "tags", List.of("cb_step_up")),
                Map.of("slotId", "LB", "roleId", "fullback_balanced", "tags", List.of("fb_overlap")),
                Map.of("slotId", "LST", "roleId", "advanced_forward_attack", "tags", List.of())));
        return tactic;
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expected) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        String content = response.getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private List<Long> squad(long careerId) {
        return jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
    }

    @Test
    void aDraftRoundTripsExactlyAndCarriesAnEaCode() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Long first = squad(careerId).get(0);

        JsonNode saved = call(put("/api/v1/user-tactics/2"), Map.of("careerId", careerId, "name", "Kontra 4-4-2", "tactic", tactic(),
                "lineup", Map.of("GK", first)), token, 200).at("/data");
        JsonNode listed = call(get("/api/v1/user-tactics?careerId=" + careerId), null, token, 200).at("/data/0");

        for (JsonNode t : List.of(saved, listed)) {
            assertThat(t.at("/name").asText()).isEqualTo("Kontra 4-4-2");
            assertThat(t.at("/formation").asText()).isEqualTo("4-4-2");
            assertThat(t.at("/buildUp").asText()).isEqualTo("Counter");
            assertThat(t.at("/depth").asInt()).isEqualTo(35);
            assertThat(t.at("/presetId").isNull()).isTrue();
            assertThat(t.at("/main").asBoolean()).isFalse();
            assertThat(t.at("/lineup/GK").asLong()).as("diziliş anahtarı base_player.id").isEqualTo(first);
            assertThat(t.at("/eaCode").asText()).as("taslaktan kayıtta EA kodu üretilir").isNotBlank();
            assertThat(t.at("/sourceReplicaId").isNull()).isTrue();
            assertThat(t.at("/slots")).hasSize(3);
            assertThat(t.at("/slots/0/slot").asText()).isEqualTo("LCB");
            assertThat(t.at("/slots/0/position").asText()).isEqualTo("CB");
            assertThat(t.at("/slots/0/roleId").asText()).isEqualTo("defender_defend");
            assertThat(t.at("/slots/0/tags/0").asText()).isEqualTo("cb_step_up");
            assertThat(t.at("/slots/2/tags")).isEmpty();
        }
    }

    @Test
    void replicaAndDraftBodiesAreExclusiveAndReplicaSlotsHaveEmptyTags() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        String replicaId = firstReplicaId(token);

        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "replicaId", replicaId, "tactic", tactic()), token, 400);
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId), token, 400);

        JsonNode fromReplica = call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "replicaId", replicaId), token, 200).at("/data");
        assertThat(fromReplica.at("/sourceReplicaId").asText()).isEqualTo(replicaId);
        fromReplica.at("/slots").forEach(s -> assertThat(s.at("/tags").isArray() && s.at("/tags").isEmpty()).as("replika slotunda tags boş dizi").isTrue());
    }

    private String firstReplicaId(String token) throws Exception {
        return call(get("/api/v1/replicas"), null, token, 200).at("/data/0/id").asText();
    }

    /** REVIEW (Orta): eşzamanlı "ana taktik yap" (çift tık, iki sekme) kısmi unique index'e çarpıp sahte 409 dönmemeli; sonunda tek ana taktik. */
    @Test
    void concurrentSetMainCallsAllSucceedAndLeaveExactlyOneMain() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        for (int slot = 1; slot <= 4; slot++) {
            call(put("/api/v1/user-tactics/" + slot), Map.of("careerId", careerId, "name", "T" + slot, "tactic", tactic()), token, 200);
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 16; i++) {
                int slot = 1 + i % 4;
                results.add(pool.submit(() -> mockMvc.perform(put("/api/v1/user-tactics/" + slot + "/main?careerId=" + careerId).header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getStatus()));
            }
            for (java.util.concurrent.Future<Integer> r : results) {
                assertThat(r.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_tactic WHERE career_id = ? AND is_main", Integer.class, careerId)).isEqualTo(1);
    }

    @Test
    void oneMainTacticPerCareerAndUpsertKeepsTheFlag() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        assertThat(call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token, 200).at("/data").isNull()).as("ana taktik yok: 200 + data null").isTrue();

        call(put("/api/v1/user-tactics/2"), Map.of("careerId", careerId, "name", "A", "tactic", tactic()), token, 200);
        call(put("/api/v1/user-tactics/3"), Map.of("careerId", careerId, "name", "B", "tactic", tactic()), token, 200);
        call(put("/api/v1/user-tactics/4/main?careerId=" + careerId), null, token, 404);

        assertThat(call(put("/api/v1/user-tactics/2/main?careerId=" + careerId), null, token, 200).at("/data/main").asBoolean()).isTrue();
        assertThat(call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token, 200).at("/data/slot").asInt()).isEqualTo(2);

        call(put("/api/v1/user-tactics/3/main?careerId=" + careerId), null, token, 200);
        JsonNode all = call(get("/api/v1/user-tactics?careerId=" + careerId), null, token, 200).at("/data");
        assertThat(all.get(0).at("/main").asBoolean()).as("eski ana taktik düşer").isFalse();
        assertThat(all.get(1).at("/main").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_tactic WHERE career_id = ? AND is_main", Integer.class, careerId)).isEqualTo(1);

        call(put("/api/v1/user-tactics/3"), Map.of("careerId", careerId, "name", "B2", "tactic", tactic()), token, 200);
        JsonNode main = call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token, 200).at("/data");
        assertThat(main.at("/slot").asInt()).as("ana slotun üzerine kayıt işareti düşürmez").isEqualTo(3);
        assertThat(main.at("/name").asText()).isEqualTo("B2");

        call(delete("/api/v1/user-tactics/3?careerId=" + careerId), null, token, 200);
        assertThat(call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token, 200).at("/data").isNull()).isTrue();
    }

    @Test
    void mainEndpointsNeedACareerAndOwnership() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        call(put("/api/v1/user-tactics/2"), Map.of("careerId", careerId, "name", "A", "tactic", tactic()), token, 200);

        call(get("/api/v1/user-tactics/main"), null, token, 400);
        call(put("/api/v1/user-tactics/2/main"), null, token, 400);

        String other = support.register();
        call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, other, 404);
        call(put("/api/v1/user-tactics/2/main?careerId=" + careerId), null, other, 404);
    }

    /** Q2: okuma anında kadroda olmayan (ya da bizden kiralıkta) oyuncu dizilişten düşer; boşluğu /fit/lineup doldurur, sayı UI'ya döner. */
    @Test
    void lineupPlayersWhoLeftTheSquadAreDroppedOnRead() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        List<Long> squad = squad(careerId);
        long stranger = jdbc.queryForObject("SELECT b.id FROM base_player b WHERE b.owner_career_id IS NULL AND b.id NOT IN (SELECT base_player_id FROM user_player_state WHERE career_id = ?) LIMIT 1",
                Long.class, careerId);
        jdbc.update("UPDATE user_player_state SET loaned_out = true WHERE career_id = ? AND base_player_id = ?", careerId, squad.get(1));

        Map<String, Long> lineup = Map.of("GK", squad.get(0), "LB", squad.get(1), "LCB", stranger);
        call(put("/api/v1/user-tactics/2"), Map.of("careerId", careerId, "name", "A", "tactic", tactic(), "lineup", lineup), token, 200);
        call(put("/api/v1/user-tactics/2/main?careerId=" + careerId), null, token, 200);

        JsonNode main = call(get("/api/v1/user-tactics/main?careerId=" + careerId), null, token, 200).at("/data");
        assertThat(main.at("/lineup/GK").asLong()).isEqualTo(squad.get(0));
        assertThat(main.at("/lineup").has("LB")).as("bizden kiralıkta").isFalse();
        assertThat(main.at("/lineup").has("LCB")).as("kadroda yok").isFalse();
        assertThat(main.at("/lineupDropped").asInt()).isEqualTo(2);
    }

    @Test
    void theRecommenderPrefersTheMainTactic() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Map<String, Object> other = tactic();
        other.put("slots", List.of(Map.of("slotId", "LST", "roleId", "poacher_attack", "tags", List.of())));
        call(put("/api/v1/user-tactics/1"), Map.of("careerId", careerId, "name", "A", "tactic", tactic()), token, 200);
        call(put("/api/v1/user-tactics/4"), Map.of("careerId", careerId, "name", "B", "tactic", other), token, 200);
        call(put("/api/v1/user-tactics/4/main?careerId=" + careerId), null, token, 200);

        assertThat(slotLookup.find(careerId, "LST", "ST")).as("en küçük slot (1) değil, ana taktik (4)")
                .contains(new TacticSlotLookup.SlotRole("poacher_attack", null));
    }
}
