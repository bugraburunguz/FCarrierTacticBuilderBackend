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
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * KARIYER-UX FAZ 4b (toplu uyum): /fit/lineup slot başına `roleFit.reasons` döner; /fit/role ile aynı biçim ve sıra (en büyük mutlak katkı önce).
 * Böylece Lab/Masa/Kadro 11 ayrı /fit/role yerine tek /fit/lineup (tüm slotlar kilitli) çağırır. D-01: nedenler /fit/role'de zaten ücretsiz;
 * ücretli içgörüler (kurallar, zayıf halka, özet, atak paterni) /fit/lineup'ta hâlâ yok.
 */
@IntegrationTest
class LineupReasonsIntegrationTest {

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

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode post(String token, String path, Object body) throws Exception {
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    @Test
    void lockedLineupCarriesPerSlotReasonsLikeFitRoleAndNoPaidInsights() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Map<String, Object> tactic = Map.of("formation", "4-4-2");
        JsonNode free = post(token, "/api/v1/fit/lineup", Map.of("careerId", careerId, "tactic", tactic));
        Map<String, Long> locked = new LinkedHashMap<>();
        free.get("slots").forEach(s -> locked.put(s.get("slotId").asText(), s.get("playerId").asLong()));

        JsonNode batch = post(token, "/api/v1/fit/lineup", Map.of("careerId", careerId, "tactic", tactic, "locked", locked));

        Map<String, JsonNode> bySlot = new HashMap<>();
        batch.get("slots").forEach(s -> bySlot.put(s.get("slotId").asText(), s));
        assertThat(bySlot).hasSize(11);
        bySlot.values().forEach(s -> {
            JsonNode reasons = s.at("/roleFit/reasons");
            assertThat(reasons.isArray()).as(s.get("slotId").asText()).isTrue();
            assertThat(reasons.size()).isBetween(1, 3);
            reasons.forEach(r -> assertThat(r.asText()).matches("\\S+ [+-]\\d+\\.\\d"));
        });

        // FIT-1 madde 2: slot başına iki eksenli sınıf (etiket gibi ücretsiz; gerekçeli zayıf halka listesi ücretli kalır).
        List<String> classes = bySlot.values().stream().map(s -> s.path("weakClass").asText()).toList();
        assertThat(classes).allMatch(c -> List.of("KALITE_ACIGI", "ROL_UYUMSUZ", "IKISI", "UYGUN").contains(c));
        assertThat(classes.stream().filter(c -> c.equals("KALITE_ACIGI") || c.equals("IKISI"))).as("11'de alt üçte bir = 3").hasSize(3);

        for (String slotId : List.of("GK", "LST")) {
            JsonNode slot = bySlot.get(slotId);
            JsonNode single = post(token, "/api/v1/fit/role", Map.of("playerId", slot.get("playerId").asLong(), "roleId", slot.get("roleId").asText(),
                    "position", slot.get("position").asText(), "engineVersion", "v3", "careerId", careerId));
            List<String> expected = new ArrayList<>();
            single.at("/roleFit/reasons").forEach(r -> expected.add(r.asText()));
            List<String> actual = new ArrayList<>();
            slot.at("/roleFit/reasons").forEach(r -> actual.add(r.asText()));
            assertThat(actual).as(slotId + " /fit/role ile aynı neden ve sıra").isEqualTo(expected);
        }

        assertThat(batch.path("rules").isMissingNode() || batch.path("rules").isNull() || batch.path("rules").isEmpty()).as("kurallar ücretli").isTrue();
        assertThat(batch.path("weakLinks").isMissingNode() || batch.path("weakLinks").isNull() || batch.path("weakLinks").isEmpty()).as("zayıf halka ücretli").isTrue();
        assertThat(batch.path("summary").isMissingNode() || batch.path("summary").isNull()).as("özet ücretli").isTrue();
        assertThat(batch.path("attackPattern").isMissingNode() || batch.path("attackPattern").isNull()).as("atak paterni ücretli").isTrue();
    }

    @Test
    void partialLockClassifiesOnlyLockedSlots() throws Exception {
        // FIT-1 inceleme: çözücünün yedekle doldurduğu kilitsiz slotlar (web göstermez) alt üçte biri ve medyanı kaydırmamalı.
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Map<String, Object> tactic = Map.of("formation", "4-4-2");
        JsonNode free = post(token, "/api/v1/fit/lineup", Map.of("careerId", careerId, "tactic", tactic));
        Map<String, Long> locked = new LinkedHashMap<>();
        free.get("slots").forEach(s -> {
            if (locked.size() < 6) {
                locked.put(s.get("slotId").asText(), s.get("playerId").asLong());
            }
        });

        JsonNode batch = post(token, "/api/v1/fit/lineup", Map.of("careerId", careerId, "tactic", tactic, "locked", locked));

        List<String> classified = new ArrayList<>();
        batch.get("slots").forEach(s -> {
            if (!s.path("weakClass").isMissingNode() && !s.path("weakClass").isNull()) {
                classified.add(s.get("slotId").asText());
            }
        });
        assertThat(classified).containsExactlyInAnyOrderElementsOf(locked.keySet());
        assertThat(batch.findValuesAsText("weakClass").stream().filter(c -> c.equals("KALITE_ACIGI") || c.equals("IKISI"))).as("6'da alt üçte bir = 2").hasSize(2);
    }
}
