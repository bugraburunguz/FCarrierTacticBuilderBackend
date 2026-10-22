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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * KARIYER-UX FAZ 2 (Q2): /fit/lineup opsiyonel `locked: {slotId: playerId}`. Kullanıcının dizdiği oyuncular sabit kalır, kalan slotları çözücü doldurur.
 * Alan yoksa davranış aynı (geriye uyumlu). Kadroda olmayan ya da formasyonda olmayan slot için kilit yok sayılır.
 */
@IntegrationTest
class LineupLockIntegrationTest {

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

    private Map<String, Long> lineup(String token, long careerId, Map<String, Long> locked) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("careerId", careerId);
        body.put("tactic", Map.of("formation", "4-4-2"));
        if (locked != null) {
            body.put("locked", locked);
        }
        var response = mockMvc.perform(post("/api/v1/fit/lineup").header("Authorization", "Bearer " + token).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        JsonNode slots = objectMapper.readTree(response.getContentAsString()).at("/data/slots");
        Map<String, Long> out = new HashMap<>();
        slots.forEach(s -> {
            if (s.hasNonNull("playerId")) {
                out.put(s.get("slotId").asText(), s.get("playerId").asLong());
            }
        });
        return out;
    }

    @Test
    void lockedPlayersStayAndTheRestIsFilled() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);

        Map<String, Long> free = lineup(token, careerId, null);
        assertThat(free).hasSize(11);
        assertThat(lineup(token, careerId, Map.of())).as("boş kilit = eski davranış").isEqualTo(free);

        long bench = squad.stream().filter(id -> !free.containsValue(id)).findFirst().orElseThrow();
        Map<String, Long> locked = lineup(token, careerId, Map.of("LST", bench, "GK", free.get("GK")));

        assertThat(locked.get("LST")).as("yedek oyuncu kilitli slotta").isEqualTo(bench);
        assertThat(locked.get("GK")).isEqualTo(free.get("GK"));
        assertThat(locked).hasSize(11);
        assertThat(locked.values()).doesNotHaveDuplicates();
    }

    @Test
    void locksForPlayersOutsideTheSquadOrUnknownSlotsAreIgnored() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        long stranger = jdbc.queryForObject("SELECT b.id FROM base_player b WHERE b.owner_career_id IS NULL AND b.id NOT IN (SELECT base_player_id FROM user_player_state WHERE career_id = ?) LIMIT 1",
                Long.class, careerId);
        Map<String, Long> free = lineup(token, careerId, null);

        Map<String, Long> withBadLocks = lineup(token, careerId, Map.of("LST", stranger, "XX", free.get("GK")));

        assertThat(withBadLocks).isEqualTo(free);
    }
}
