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
        var request = method.equals("GET") ? get(path) : post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        var response = mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    @Test
    void youngsterWithHeadroomGetsAnEstimatedPlanComparedAgainstBalanced() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        // Fikstür: yalnız bu kariyerde — oyun tarihi 6 yıl geri (oyuncular gençleşir), potansiyel içe aktarmayla (OVR + 15).
        JsonNode squad = call(token, "GET", "/api/v1/careers/" + careerId + "/squad", null);
        StringBuilder players = new StringBuilder();
        StringBuilder links = new StringBuilder();
        squad.forEach((m) -> {
            // İçe aktarma EA kimliğiyle eşler: sahte katalogda ea_resource_id = FIRST_MOCK_ID + id − 1.
            long id = CareerTestSupport.FIRST_MOCK_ID + m.at("/player/id").asLong() - 1;
            int ovr = m.at("/player/overall").asInt();
            players.append(id).append(',').append(ovr).append(',').append(Math.min(99, ovr + 15)).append('\n');
            links.append(id).append(",1\n");
        });
        assertThat(support.importCsv(careerId, token, players.toString(), links.toString(), null).path("success").asBoolean()).isTrue();
        support.json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/careers/" + careerId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"gameDate\":\"2020-07-01\"}"), token);
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
            assertThat(r.get("text").asText()).contains("2 sezonda");
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
