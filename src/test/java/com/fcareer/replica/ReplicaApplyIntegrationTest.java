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
    @Autowired
    private com.fcareer.catalog.service.PlayerService playerService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "rep-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
    }

    private List<Long> catalogPool(int n) {
        // Yalnız katalog oyuncusu: kariyerde üretilen oyuncular (owner_career_id) aynı adı taşıyabilir (ör. isim sözlüğü testi "Mock Player 1").
        return jdbc.queryForList("SELECT id FROM base_player WHERE owner_career_id IS NULL AND full_name ~ '^Mock Player [0-9]+$' AND substring(full_name from 13)::int >= 1 ORDER BY substring(full_name from 13)::int LIMIT " + n, Long.class);
    }

    /** Kadrosu en kalabalık kulüp: diğer testler katalogu değiştirebilir. */
    private int richClub() {
        // Yalnız katalog oyuncuları sayılır: içe aktarmanın ürettiği (owner_career_id) oyuncular yeni kariyerin kadrosuna girmez.
        return jdbc.queryForObject("SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.club_id IS NOT NULL "
                + "AND b.owner_career_id IS NULL GROUP BY r.club_id ORDER BY count(*) DESC, r.club_id LIMIT 1", Integer.class);
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

    /** REP-b: doğal mevkiler önce doldurulur; mevki dışı yalnız gerekiyorsa. Doğal atama sayısı, slotlar ile oyuncuların doğal mevki eşleşmesinin en büyük eşleşmesine eşit olmalı (doğal LW varken ST sol kanada atanmaz). */
    @Test
    void naturalPositionsAreFilledFirst_outOfPositionOnlyWhenNeeded() throws Exception {
        String token = register();
        for (String id : List.of("guardiola_barcelona_2011", "wenger_arsenal_2008", "mourinho_inter_2010", "zidane_real_2017", "klopp_liverpool_2020", "flick_bayern_2020", "ferguson_united_2008", "conte_chelsea_2017")) {
            JsonNode data = call(post("/api/v1/replicas/" + id + "/apply"), Map.of("playerIds", catalogPool(60)), token, 200).path("data");
            List<String> slotPositions = new ArrayList<>();
            List<Set<String>> natural = new ArrayList<>();
            int naturalAssigned = 0;
            for (JsonNode s : data.path("slots")) {
                slotPositions.add(s.path("position").asText());
                Set<String> positions = new HashSet<>();
                s.at("/player/positions").forEach((p) -> positions.add(p.asText()));
                natural.add(positions);
                boolean inPosition = positions.contains(s.path("position").asText());
                assertThat(s.path("outOfPosition").asBoolean()).as(id + " " + s.path("slotId").asText()).isEqualTo(!inPosition);
                if (!inPosition) {
                    assertThat(s.path("playerPosition").asText()).as("etiket için oyuncunun mevkisi").isNotBlank();
                }
                naturalAssigned += inPosition ? 1 : 0;
            }
            assertThat(naturalAssigned).as(id + ": doğal atama sayısı en büyük eşleşme").isEqualTo(maxNaturalMatching(slotPositions, catalogPositions(60)));
        }
    }

    private List<Set<String>> catalogPositions(int n) {
        List<Set<String>> out = new ArrayList<>();
        playerService.fitPlayers(catalogPool(n), null, null).forEach((p) -> out.add(new HashSet<>(p.getPositions())));
        return out;
    }

    /** Slot mevkileri ile oyuncu doğal mevkileri arasında en büyük eşleşme (artan yol). */
    private static int maxNaturalMatching(List<String> slotPositions, List<Set<String>> players) {
        int[] owner = new int[players.size()];
        java.util.Arrays.fill(owner, -1);
        int size = 0;
        for (int s = 0; s < slotPositions.size(); s++) {
            if (augment(s, slotPositions, players, owner, new boolean[players.size()])) {
                size++;
            }
        }
        return size;
    }

    private static boolean augment(int slot, List<String> slotPositions, List<Set<String>> players, int[] owner, boolean[] seen) {
        for (int p = 0; p < players.size(); p++) {
            if (!seen[p] && players.get(p).contains(slotPositions.get(slot))) {
                seen[p] = true;
                if (owner[p] < 0 || augment(owner[p], slotPositions, players, owner, seen)) {
                    owner[p] = slot;
                    return true;
                }
            }
        }
        return false;
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
