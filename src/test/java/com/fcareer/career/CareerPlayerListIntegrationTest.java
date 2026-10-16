package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.roles.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V3-06a (API CONTRACT CHANGE): kariyer oyuncu listesinin E3-10b filtreleri — value_min, kiralık, oyun tarihine göre sözleşme,
 * ulaşılabilirlik kovası (öneri hattının sınıflandırıcısı), FitEngineV3 ile rol sıralaması ve /players/facets.
 * Kendi kariyerini kurar; kulübü sorguyla (oyunculu) seçer, sıra bağımlılığı yok.
 */
@IntegrationTest
class CareerPlayerListIntegrationTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2027, 1, 15);

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
    private RoleService roleService;
    @Autowired
    private CatalogProperties catalogProperties;
    @Autowired
    private com.fcareer.fitengine.level.FitLevelNormsBuilder levelNormsBuilder;
    @Autowired
    private com.fcareer.fitengine.level.LevelFitService levelFitService;

    private String token;
    private long careerId;

    @BeforeEach
    void setUp() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        token = call(post("/api/v1/auth/register"), Map.of("email", "list-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
        Integer clubId = jdbc.queryForObject("SELECT club_id FROM player_rating WHERE game_version_id = ? GROUP BY club_id ORDER BY count(*) DESC, club_id LIMIT 1",
                Integer.class, catalogProperties.getDefaultGameVersion());
        careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 2_000_000), token, 200).at("/data/id").asLong();
        call(get("/api/v1/players?career=" + careerId + "&size=1"), null, token, 200); // kayıt havuzunu tohumla
    }

    @Test
    void valueMinFiltersFromBelow() throws Exception {
        // Test verisinde değer yok: üç havuz oyuncusuna kendi MODELED değerini yazar, sonunda yalnız bunları geri alır (paylaşılan context).
        List<Long> ids = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ? ORDER BY player_id LIMIT 3", Long.class, careerId);
        long[] values = {1_000_000L, 3_000_000L, 9_000_000L};
        short gv = catalogProperties.getDefaultGameVersion();
        try {
            for (int i = 0; i < ids.size(); i++) {
                jdbc.update("INSERT INTO player_valuation (base_player_id, game_version_id, source, value_eur, model_version) VALUES (?, ?, 'MODELED', ?, 'v3-06a-test')", ids.get(i), gv, values[i]);
            }
            List<Long> filtered = idsOf(call(get("/api/v1/players?career=" + careerId + "&value_min=3000000&size=200"), null, token, 200));
            assertThat(filtered).contains(ids.get(1), ids.get(2)).doesNotContain(ids.get(0));
            call(get("/api/v1/players?career=" + careerId + "&value_min=3000000&size=200"), null, token, 200).at("/data/items")
                    .forEach((p) -> assertThat(p.at("/valueEur").asLong()).isGreaterThanOrEqualTo(3_000_000L));
        } finally {
            jdbc.update("DELETE FROM player_valuation WHERE model_version = 'v3-06a-test'");
        }
    }

    @Test
    void facetsTellWhichFiltersHaveData() throws Exception {
        JsonNode empty = call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data");
        assertThat(empty.at("/contractData").asBoolean()).isFalse();
        assertThat(empty.at("/loanData").asBoolean()).isFalse();
        assertThat(empty.has("gameDate")).isFalse();
        assertThat(empty.at("/budgetEur").asLong()).isEqualTo(2_000_000L);

        long someone = anyPlayer();
        jdbc.update("UPDATE career_player SET contract_until = ?, loan_from_team_id = 1 WHERE career_id = ? AND player_id = ?", GAME_DATE.plusMonths(3), careerId, someone);
        jdbc.update("UPDATE career SET current_game_date = ? WHERE id = ?", GAME_DATE, careerId);
        JsonNode filled = call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data");
        assertThat(filled.at("/contractData").asBoolean()).isTrue();
        assertThat(filled.at("/loanData").asBoolean()).isTrue();
        assertThat(filled.at("/gameDate").asText()).isEqualTo(GAME_DATE.toString());
    }

    @Test
    void loanAndContractFiltersUseTheSaveDataAndTheGameDate() throws Exception {
        List<Long> ids = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ? ORDER BY player_id LIMIT 3", Long.class, careerId);
        jdbc.update("UPDATE career SET current_game_date = ? WHERE id = ?", GAME_DATE, careerId);
        jdbc.update("UPDATE career_player SET contract_until = ? WHERE career_id = ? AND player_id = ?", GAME_DATE.plusMonths(4), careerId, ids.get(0));
        jdbc.update("UPDATE career_player SET contract_until = ? WHERE career_id = ? AND player_id = ?", GAME_DATE.plusMonths(18), careerId, ids.get(1));
        jdbc.update("UPDATE career_player SET loan_from_team_id = 1 WHERE career_id = ? AND player_id = ?", careerId, ids.get(2));

        List<Long> expiring = idsOf(call(get("/api/v1/players?career=" + careerId + "&contract_months=6&size=200"), null, token, 200));
        assertThat(expiring).contains(ids.get(0)).doesNotContain(ids.get(1));
        List<Long> loans = idsOf(call(get("/api/v1/players?career=" + careerId + "&on_loan=true&size=200"), null, token, 200));
        assertThat(loans).containsExactly(ids.get(2));

        JsonNode row = call(get("/api/v1/players?career=" + careerId + "&contract_months=6&size=200"), null, token, 200).at("/data/items/0");
        assertThat(row.at("/contractUntil").asText()).isEqualTo(GAME_DATE.plusMonths(4).toString());
    }

    @Test
    void feasibilityBucketIsAColumnAndAFilterFromTheRecommendationClassifier() throws Exception {
        JsonNode page = call(get("/api/v1/players?career=" + careerId + "&size=60&sort=overall"), null, token, 200).at("/data/items");
        List<String> buckets = new ArrayList<>();
        page.forEach((p) -> buckets.add(p.at("/feasibility").asText()));
        assertThat(buckets).allMatch((b) -> List.of("ALINABILIR", "ZOR", "HAYAL").contains(b));

        JsonNode dreams = call(get("/api/v1/players?career=" + careerId + "&bucket=HAYAL&size=200"), null, token, 200).at("/data");
        assertThat(dreams.at("/totalItems").asLong()).isPositive();
        dreams.at("/items").forEach((p) -> assertThat(p.at("/feasibility").asText()).isEqualTo("HAYAL"));
    }

    @Test
    void roleSortRanksThePoolByFitEngineV3() throws Exception {
        FitRole role = roleService.fitRolesById().values().stream().filter((r) -> r.getPositions().contains("ST")).findFirst().orElseThrow();
        JsonNode data = call(get("/api/v1/players?career=" + careerId + "&sort=role&role=" + role.getId() + "&size=30"), null, token, 200).at("/data");
        JsonNode items = data.at("/items");
        assertThat(items.size()).isPositive();
        double previous = Double.MAX_VALUE;
        for (JsonNode p : items) {
            double fit = p.at("/roleFit").asDouble();
            assertThat(fit).isLessThanOrEqualTo(previous);
            previous = fit;
        }
        JsonNode second = call(get("/api/v1/players?career=" + careerId + "&sort=role&role=" + role.getId() + "&size=30&page=1"), null, token, 200).at("/data/items");
        if (second.size() > 0) {
            assertThat(second.get(0).at("/roleFit").asDouble()).isLessThanOrEqualTo(previous);
        }
        call(get("/api/v1/players?career=" + careerId + "&sort=role&role=yok_boyle_rol"), null, token, 404);
    }

    /** V3-07c (API CONTRACT CHANGE): wonderkid satırları kariyerde aynı sınıflandırıcının kovasını taşır (oyuncu sayfasıyla aynı); kadrodakilerde kova yok. */
    @Test
    void wonderkidsCarryTheSameFeasibilityBucketAsThePlayerPage() throws Exception {
        givePotentials();
        JsonNode rows = call(get("/api/v1/wonderkids?career=" + careerId + "&maxAge=40&minPot=1&limit=8&sort=potential"), null, token, 200).at("/data");
        assertThat(rows.size()).isPositive();
        int withBucket = 0;
        for (JsonNode row : rows) {
            long id = row.at("/id").asLong();
            String expected = call(get("/api/v1/players/" + id + "/contract?career=" + careerId), null, token, 200).at("/data/feasibility").asText(null);
            assertThat(row.at("/feasibility").asText(null)).as("oyuncu " + id).isEqualTo(expected);
            withBucket += expected == null ? 0 : 1;
        }
        assertThat(withBucket).isPositive();
        JsonNode anonymous = call(get("/api/v1/wonderkids?maxAge=40&minPot=1&limit=3"), null, null, 200).at("/data");
        anonymous.forEach((row) -> assertThat(row.has("feasibility")).isFalse());
    }

    /** V3-07c 0a: wonderkid rol uyumu etiketi ve gösterilecek yüzde aynı sayıdan (roleLevelFit); rozet rengi etiketten türer. */
    @Test
    void wonderkidRoleLabelAndPercentShareOneBasis() throws Exception {
        givePotentials();
        if (jdbc.queryForObject("SELECT count(*) FROM fit_level_norms", Integer.class) == 0) {
            levelNormsBuilder.rebuild(catalogProperties.getDefaultGameVersion());
            levelFitService.refresh();
        }
        JsonNode rows = call(get("/api/v1/wonderkids?career=" + careerId + "&maxAge=40&minPot=1&limit=8&role=poacher_attack&pos=ST"), null, token, 200).at("/data");
        assertThat(rows.size()).isPositive();
        FitEngineProperties.V3 v3 = new FitEngineProperties().getV3();
        assertThat(rows.findValues("roleLevelFit")).as("seviye normuyla en az bir satır").isNotEmpty();
        for (JsonNode row : rows) {
            if (!row.has("roleLevelFit")) {
                // Seviye normu yoksa (test veritabanı) ne etiket ne renk: ekran ham rol puanını etiketsiz gösterir.
                assertThat(row.has("roleFitLabel")).as(row.toString()).isFalse();
                assertThat(row.has("roleBadge")).as(row.toString()).isFalse();
                continue;
            }
            RoleFitV3Service.FitLabel label = RoleFitV3Service.label(row.at("/roleLevelFit").asDouble(), v3);
            assertThat(row.at("/roleFitLabel").asText()).isEqualTo(label.name());
            assertThat(row.at("/roleBadge").asText(null)).isEqualTo(label.badge() == null ? null : label.badge().name());
        }
    }

    /** Kariyer havuzunda potansiyel içe aktarmadan gelir (cp.pot); test kendi kariyerinin oyuncularına yazar (bu kariyere özgü satırlar). */
    private void givePotentials() {
        jdbc.update("UPDATE career_player SET pot = LEAST(ovr + 12, 99) WHERE career_id = ? AND pot IS NULL AND ovr IS NOT NULL", careerId);
    }

    private long anyPlayer() {
        return jdbc.queryForObject("SELECT player_id FROM career_player WHERE career_id = ? ORDER BY player_id LIMIT 1", Long.class, careerId);
    }

    private static List<Long> idsOf(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.at("/data/items").forEach((p) -> ids.add(p.at("/id").asLong()));
        return ids;
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String bearer, int status) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        var result = mockMvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
