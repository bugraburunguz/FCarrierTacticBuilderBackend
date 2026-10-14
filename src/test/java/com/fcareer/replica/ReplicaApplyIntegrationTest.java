package com.fcareer.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** REP-07: "Kadromla uygula": replikanın 11 rolüne kullanıcının oyuncuları Hungarian (v3 currentFit) ile atanır; kadro yoksa katalogdan "örnek". */
@IntegrationTest
class ReplicaApplyIntegrationTest {

    private static final String REPLICA = "guardiola_barcelona_2011";
    private static final Set<String> STATES = Set.of("GOOD", "AT_LEVEL", "WEAK", "WRONG_ROLE", "EMPTY");
    private static final int GOLDEN_POOL = 16;

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

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "rep-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    private List<Long> catalogPool(int n) {
        return jdbc.queryForList("SELECT id FROM base_player WHERE full_name ~ '^Mock Player [0-9]+$' ORDER BY substring(full_name from 13)::int LIMIT " + n, Long.class);
    }

    /** Kadrosu en kalabalık kulüp: diğer testler katalogu değiştirebilir. */
    private int richClub() {
        return jdbc.queryForObject("SELECT club_id FROM player_rating WHERE club_id IS NOT NULL GROUP BY club_id ORDER BY count(*) DESC, club_id LIMIT 1", Integer.class);
    }

    private JsonNode apply(String token, Map<String, Object> body, int status) throws Exception {
        return call(post("/api/v1/replicas/" + REPLICA + "/apply"), body, token, status);
    }

    @Test
    void utMode_assignsElevenDistinctPlayersToTheReplicaRoles() throws Exception {
        JsonNode data = apply(register(), Map.of("playerIds", catalogPool(GOLDEN_POOL)), 200).path("data");
        assertThat(data.path("source").asText()).isEqualTo("UT");
        assertThat(data.path("slots")).hasSize(11);
        Set<Long> used = new HashSet<>();
        for (JsonNode s : data.path("slots")) {
            assertThat(s.path("roleName").asText()).isNotBlank();
            assertThat(s.path("focus").asText()).isNotBlank();
            assertThat(STATES).contains(s.path("state").asText());
            assertThat(used.add(s.at("/player/id").asLong())).as("oyuncu tek slotta").isTrue();
        }
        assertThat(data.at("/analysis/metrics/band").asText()).isNotBlank();
        assertThat(data.path("squadFit").isNumber()).isTrue();
    }

    @Test
    void careerMode_usesOnlyTheCareerSquad() throws Exception {
        String token = register();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", richClub(), "budgetEur", 50_000_000), token, 200).at("/data/id").asLong();
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ?", Long.class, careerId);
        JsonNode data = apply(token, Map.of("careerId", careerId), 200).path("data");
        assertThat(data.path("source").asText()).isEqualTo("CAREER");
        List<Long> picked = new ArrayList<>();
        data.path("slots").forEach((s) -> picked.add(s.at("/player/id").asLong()));
        assertThat(squad).containsAll(picked);
    }

    @Test
    void anotherUsersCareerIsNotFound() throws Exception {
        String owner = register();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", richClub(), "budgetEur", 1_000_000), owner, 200).at("/data/id").asLong();
        apply(register(), Map.of("careerId", careerId), 404);
    }

    @Test
    void withoutASquadItReturnsCatalogExamplesMarkedAsSample() throws Exception {
        JsonNode data = apply(register(), Map.of(), 200).path("data");
        assertThat(data.path("source").asText()).isEqualTo("SAMPLE");
        for (JsonNode s : data.path("slots")) {
            assertThat(s.at("/player/id").isNumber()).as(s.path("slotId").asText()).isTrue();
        }
    }

    @Test
    void aThinSquadLeavesEmptySlotsThatOfferASuggestion() throws Exception {
        JsonNode data = apply(register(), Map.of("playerIds", catalogPool(3)), 200).path("data");
        long empty = 0;
        for (JsonNode s : data.path("slots")) {
            if ("EMPTY".equals(s.path("state").asText())) {
                empty++;
                assertThat(s.path("suggest").asBoolean()).isTrue();
                assertThat(s.path("player").isNull()).isTrue();
            }
        }
        assertThat(empty).isEqualTo(8);
    }

    @Test
    void aCareerIdAndPlayerIdsTogetherAreRejected() throws Exception {
        apply(register(), Map.of("careerId", 1, "playerIds", List.of(1)), 400);
    }

    /** Sabit kadro (katalogun ilk 16 oyuncusu) + sabit replika → beklenen atama (slot → oyuncu adı). */
    @Test
    void goldenAssignmentForAFixedSquadAndReplica() throws Exception {
        JsonNode data = apply(register(), Map.of("playerIds", catalogPool(GOLDEN_POOL)), 200).path("data");
        Map<String, String> actual = new LinkedHashMap<>();
        data.path("slots").forEach((s) -> actual.put(s.path("slotId").asText(), s.at("/player/name").asText()));
        try (InputStream in = new ClassPathResource("replica-apply-golden.json").getInputStream()) {
            Map<String, String> expected = objectMapper.convertValue(objectMapper.readTree(in), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() {
            });
            assertThat(actual).isEqualTo(expected);
        }
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
