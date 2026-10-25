package com.fcareer.development;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** FIT-3 madde 11: kariyer kadrosunda kapsamdaki gençler için plan önerisi; zayıf slotlar verilince o slotun rol planı ve slottaki oyuncunun tahmini. */
@IntegrationTest
class DevelopmentRecommendationIntegrationTest {

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

    private JsonNode call(String token, String method, String path, Object body) throws Exception {
        var request = method.equals("GET") ? get(path)
                : (method.equals("PATCH_SPOILER") ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path) : post(path))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        var response = mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    /**
     * Fikstür: yalnız bu kariyerde — potansiyel içe aktarmayla (OVR + boost), oyun tarihi 6 yıl geri (oyuncular gençleşir). Ortak katalog değişmez.
     * Dönüş: oyuncu id → {OVR, yaş}.
     */
    private Map<Long, int[]> youthCareer(String token, long careerId, int boost) throws Exception {
        JsonNode squad = call(token, "GET", "/api/v1/careers/" + careerId + "/squad", null);
        StringBuilder players = new StringBuilder();
        StringBuilder links = new StringBuilder();
        squad.forEach((m) -> {
            // İçe aktarma EA kimliğiyle eşler: sahte katalogda ea_resource_id = FIRST_MOCK_ID + id − 1.
            long id = CareerTestSupport.FIRST_MOCK_ID + m.at("/player/id").asLong() - 1;
            int ovr = m.at("/player/overall").asInt();
            players.append(id).append(',').append(ovr).append(',').append(Math.min(99, ovr + boost)).append('\n');
            links.append(id).append(",1\n");
        });
        assertThat(support.importCsv(careerId, token, players.toString(), links.toString(), null).path("success").asBoolean()).isTrue();
        support.json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/careers/" + careerId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"gameDate\":\"2020-07-01\"}"), token);
        Map<Long, int[]> out = new java.util.HashMap<>();
        call(token, "GET", "/api/v1/careers/" + careerId + "/squad", null)
                .forEach((m) -> out.put(m.at("/player/id").asLong(), new int[]{m.at("/player/overall").asInt(), m.at("/player/age").asInt()}));
        return out;
    }

    @Test
    void tooManyWeakSlotsIsRejected() throws Exception {
        // İnceleme: istek boyutu sınırı (en çok 11 slot) doğrulanır; büyük gövde CPU'yu tüketmesin.
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(Map.of("slotId", "S" + i, "position", "CM", "roleId", "box_to_box_balanced"));
        }
        var response = mockMvc.perform(post("/api/v1/careers/" + careerId + "/development/recommendations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(Map.of("weakSlots", many)))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void rangedSpoilerModeScopesAndProjectsByTheBandFloor() throws Exception {
        // İnceleme: SCOUT_RANGE kariyerinde kesin potansiyel kullanılmaz; kapsam ve tahmin bandın alt sınırıyla (SpoilerPolicy.low).
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        Map<Long, int[]> squad = youthCareer(token, careerId, 6);
        call(token, "PATCH_SPOILER", "/api/v1/careers/" + careerId, Map.of("spoilerMode", "SCOUT_RANGE"));
        JsonNode recs = call(token, "POST", "/api/v1/careers/" + careerId + "/development/recommendations", Map.of());
        List<Long> expected = squad.entrySet().stream()
                .filter((e) -> e.getValue()[1] <= 23 && com.fcareer.career.desk.SpoilerPolicy.low(e.getValue()[0] + 6) - e.getValue()[0] >= 5)
                .map(Map.Entry::getKey).sorted().toList();
        List<Long> actual = new ArrayList<>();
        recs.forEach((r) -> actual.add(r.get("playerId").asLong()));
        assertThat(actual.stream().sorted().toList()).isEqualTo(expected);
    }

    @Test
    void youngsterWithHeadroomGetsAnEstimatedPlanComparedAgainstBalanced() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        youthCareer(token, careerId, 15);
        JsonNode lineup = call(token, "POST", "/api/v1/fit/lineup", Map.of("careerId", careerId, "tactic", Map.of("formation", "4-4-2")));
        List<Map<String, Object>> weak = new ArrayList<>();
        lineup.get("slots").forEach((s) -> {
            if (List.of("KALITE_ACIGI", "IKISI").contains(s.path("weakClass").asText())) {
                weak.add(Map.of("slotId", s.get("slotId").asText(), "position", s.get("position").asText(), "roleId", s.get("roleId").asText(),
                        "playerId", s.get("playerId").asLong()));
            }
        });
        assertThat(weak).isNotEmpty();

        JsonNode recs = call(token, "POST", "/api/v1/careers/" + careerId + "/development/recommendations", Map.of("weakSlots", weak));

        assertThat(recs.isArray() && recs.size() > 0).as("kadroda kapsamda genç var").isTrue();
        boolean slotOptionSeen = false;
        for (JsonNode r : recs) {
            assertThat(r.get("age").asInt()).isLessThanOrEqualTo(23);
            assertThat(r.get("estimated").asBoolean()).isTrue();
            assertThat(r.get("text").asText()).containsAnyOf("2 sezonda", "Henüz net bir mevkisi yok, esnek gelişsin");
            assertThat(r.get("reason").asText()).isIn("CLEAR_TARGET", "NO_CLEAR_TARGET", "SMALL_GAIN", "NO_OPTIONS");
            assertThat(r.has("potential")).as("spoiler: ham potansiyel dönmez").isFalse();
            if (!r.get("balanced").asBoolean()) {
                assertThat(r.at("/recommended/gain").asDouble()).isGreaterThanOrEqualTo(2.0);
            }
            for (JsonNode o : r.get("options")) {
                assertThat(o.get("gain").asDouble()).isCloseTo(o.get("strength2").asDouble() - o.get("balancedStrength2").asDouble(), org.assertj.core.api.Assertions.within(0.11));
                if (o.hasNonNull("incumbentName")) {
                    slotOptionSeen = true;
                    assertThat(o.get("incumbentStrength2").asDouble()).isPositive();
                }
            }
        }
        assertThat(slotOptionSeen || recs.findValues("options").stream().allMatch(JsonNode::isEmpty)).as("zayıf slot rol planı adaylar arasında").isTrue();
    }
}
