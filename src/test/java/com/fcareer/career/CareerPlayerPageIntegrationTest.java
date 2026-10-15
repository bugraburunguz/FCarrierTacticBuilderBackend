package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.recommendation.pipeline.ClubStrengthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V3-06b (API CONTRACT CHANGE): kariyer oyuncu sayfası — GET /players/{id}/contract?career= (sözleşme ve değer, ulaşılabilirlik kovası,
 * alım sonrası bütçe; veri yoksa alan yok, ENG-02) ve kariyerde v3 rol uyumunun seviye referansı (kullanıcının ligi).
 */
@IntegrationTest
class CareerPlayerPageIntegrationTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2027, 1, 15);
    private static final long BUDGET = 2_000_000L;
    private static final String MODEL_TAG = "v3-06b-test";

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
    private CatalogProperties catalogProperties;
    @Autowired
    private ClubStrengthService clubStrengthService;

    private String token;
    private long careerId;

    @BeforeEach
    void setUp() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        token = register();
        Integer clubId = jdbc.queryForObject("SELECT club_id FROM player_rating WHERE game_version_id = ? GROUP BY club_id ORDER BY count(*) DESC, club_id LIMIT 1",
                Integer.class, catalogProperties.getDefaultGameVersion());
        careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", BUDGET), token, 200).at("/data/id").asLong();
        call(get("/api/v1/players?career=" + careerId + "&size=1"), null, token, 200); // kayıt havuzunu tohumla
    }

    @Test
    void contractOfAnOutsidePlayerUsesTheGameDateAndTheBudget() throws Exception {
        long outsider = outsider();
        short gv = catalogProperties.getDefaultGameVersion();
        jdbc.update("UPDATE career SET current_game_date = ? WHERE id = ?", GAME_DATE, careerId);
        jdbc.update("UPDATE career_player SET contract_until = ?, wage_eur = 12000, release_clause_eur = 5000000, loan_from_team_id = 1 WHERE career_id = ? AND player_id = ?",
                GAME_DATE.plusMonths(7), careerId, outsider);
        try {
            jdbc.update("INSERT INTO player_valuation (base_player_id, game_version_id, source, value_eur, model_version) VALUES (?, ?, 'MODELED', 1500000, ?)", outsider, gv, MODEL_TAG);
            JsonNode c = call(get("/api/v1/players/" + outsider + "/contract?career=" + careerId), null, token, 200).at("/data");
            assertThat(c.at("/inSquad").asBoolean()).isFalse();
            assertThat(c.at("/wageEur").asLong()).isEqualTo(12_000L);
            assertThat(c.at("/releaseClauseEur").asLong()).isEqualTo(5_000_000L);
            assertThat(c.at("/contractUntil").asText()).isEqualTo(GAME_DATE.plusMonths(7).toString());
            assertThat(c.at("/gameDate").asText()).isEqualTo(GAME_DATE.toString());
            assertThat(c.at("/monthsLeft").asInt()).isEqualTo((int) (ChronoUnit.DAYS.between(GAME_DATE, GAME_DATE.plusMonths(7)) / 30));
            assertThat(c.at("/onLoan").asBoolean()).isTrue();
            assertThat(c.at("/valueEur").asLong()).isEqualTo(1_500_000L);
            assertThat(c.at("/valuationEstimated").asBoolean()).isTrue();
            assertThat(c.at("/feasibility").asText()).isIn("ALINABILIR", "ZOR", "HAYAL");
            assertThat(c.at("/budgetEur").asLong()).isEqualTo(BUDGET);
            assertThat(c.at("/budgetAfterPurchaseEur").asLong()).isEqualTo(BUDGET - 1_500_000L);
        } finally {
            jdbc.update("DELETE FROM player_valuation WHERE model_version = ?", MODEL_TAG);
        }
    }

    @Test
    void missingDataIsAbsentNotZero() throws Exception {
        long outsider = outsider();
        jdbc.update("UPDATE career_player SET contract_until = NULL, wage_eur = NULL, release_clause_eur = NULL, loan_from_team_id = NULL WHERE career_id = ? AND player_id = ?",
                careerId, outsider);
        JsonNode c = call(get("/api/v1/players/" + outsider + "/contract?career=" + careerId), null, token, 200).at("/data");
        for (String field : List.of("wageEur", "releaseClauseEur", "contractUntil", "monthsLeft", "gameDate", "valueEur", "budgetAfterPurchaseEur")) {
            assertThat(c.has(field)).as(field).isFalse();
        }
        assertThat(c.at("/onLoan").asBoolean()).isFalse();
    }

    @Test
    void squadPlayerIsMarkedAndHasNoPurchaseLines() throws Exception {
        long member = jdbc.queryForObject("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id LIMIT 1", Long.class, careerId);
        JsonNode c = call(get("/api/v1/players/" + member + "/contract?career=" + careerId), null, token, 200).at("/data");
        assertThat(c.at("/inSquad").asBoolean()).isTrue();
        assertThat(c.has("feasibility")).isFalse();
        assertThat(c.has("budgetAfterPurchaseEur")).isFalse();
    }

    @Test
    void unknownBudgetHasNoBudgetLines() throws Exception {
        call(patch("/api/v1/careers/" + careerId), Map.of("budgetEur", 0), token, 200);
        JsonNode c = call(get("/api/v1/players/" + outsider() + "/contract?career=" + careerId), null, token, 200).at("/data");
        assertThat(c.has("budgetEur")).isFalse();
        assertThat(c.has("budgetAfterPurchaseEur")).isFalse();
    }

    @Test
    void anotherUsersCareerIsNotVisible() throws Exception {
        String stranger = register();
        call(get("/api/v1/players/" + outsider() + "/contract?career=" + careerId), null, stranger, 404);
    }

    /** V3-06c/0a kararı: levelFit referansı her yerde oyuncunun kendi OVR bandı; kariyer bağlamı katalogla aynı sonucu verir (tohumlanmış kariyerde öznitelikler aynı). */
    @Test
    void careerRolesLevelFitEqualsTheCatalog() throws Exception {
        long outsider = outsider();
        JsonNode career = call(get("/api/v1/players/" + outsider + "/roles?career=" + careerId + "&engine=v3"), null, token, 200).at("/data");
        JsonNode catalog = call(get("/api/v1/players/" + outsider + "/roles?engine=v3"), null, token, 200).at("/data");
        assertThat(career.size()).isPositive().isEqualTo(catalog.size());
        for (int i = 0; i < career.size(); i++) {
            assertThat(career.get(i).at("/roleId").asText()).isEqualTo(catalog.get(i).at("/roleId").asText());
            assertThat(career.get(i).at("/levelFit").asDouble()).as(career.get(i).at("/roleId").asText()).isEqualTo(catalog.get(i).at("/levelFit").asDouble());
            assertThat(career.get(i).at("/fitLabel").asText()).isEqualTo(catalog.get(i).at("/fitLabel").asText());
            assertThat(career.get(i).has("levelReference")).isFalse();
        }
    }

    /** Lig seviyesi göstergesinin girdisi: facets kullanıcının ligini ve lig gücünü (OVR) verir; bilinmiyorsa alan yok. */
    @Test
    void facetsCarryTheUserLeagueStrength() throws Exception {
        JsonNode facets = call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data");
        String userLeague = jdbc.queryForObject("SELECT l.name FROM career k JOIN club c ON c.id = k.club_id JOIN league l ON l.id = c.league_id WHERE k.id = ?",
                String.class, careerId);
        Double leagueStrength = clubStrengthService.of(careerId).leagueStrength(userLeague);
        // Paylaşılan test veritabanında (sıraya göre) ligde yeterli kulüp olmayabilir: o zaman alanlar yok (0 değil, ENG-02).
        if (leagueStrength == null) {
            assertThat(facets.has("leagueName")).isFalse();
            assertThat(facets.has("leagueOvr")).isFalse();
            return;
        }
        assertThat(facets.at("/leagueName").asText()).isEqualTo(userLeague);
        assertThat(facets.at("/leagueOvr").asInt()).isEqualTo((int) Math.round(leagueStrength));
    }

    /**
     * Kök neden (V3-06c kapısı): yeni kariyerde havuz ilk oyuncu sorgusunda tohumlanır; facets ondan önce çağrılırsa kulüp/lig gücü boş havuzdan
     * hesaplanıp data_version anahtarıyla önbellekte kalıyordu (tohumlama sürümü artırmaz). Güç hesabı havuzu önce tohumlamalı.
     */
    @Test
    void leagueStrengthIsKnownEvenWhenFacetsComeBeforeAnyPlayerQuery() throws Exception {
        Integer clubId = jdbc.queryForObject("SELECT club_id FROM career WHERE id = ?", Integer.class, careerId);
        long fresh = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", BUDGET), token, 200).at("/data/id").asLong();
        JsonNode first = call(get("/api/v1/players/facets?career=" + fresh), null, token, 200).at("/data");
        call(get("/api/v1/players?career=" + fresh + "&size=1"), null, token, 200);
        JsonNode after = call(get("/api/v1/players/facets?career=" + fresh), null, token, 200).at("/data");
        JsonNode seeded = call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data");
        assertThat(first.has("leagueOvr")).isEqualTo(seeded.has("leagueOvr"));
        assertThat(first.at("/leagueOvr").asInt()).isEqualTo(seeded.at("/leagueOvr").asInt());
        assertThat(after.at("/leagueOvr").asInt()).isEqualTo(first.at("/leagueOvr").asInt());
    }

    /** V3-06d Masa dönem bandı: kadronun toplam maaşı yalnız kadrodaki herkesin maaşı kayıtta varsa (eksikse alan yok; 0 değil, ENG-02). */
    @Test
    void facetsCarrySquadWageOnlyWhenEveryWageIsKnown() throws Exception {
        assertThat(call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data").has("squadWageEur")).isFalse();
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY id", Long.class, careerId);
        for (Long id : squad) {
            jdbc.update("UPDATE career_player SET wage_eur = 1000 WHERE career_id = ? AND player_id = ?", careerId, id);
        }
        jdbc.update("UPDATE career SET data_version = data_version + 1 WHERE id = ?", careerId);
        assertThat(call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data/squadWageEur").asLong()).isEqualTo(1000L * squad.size());
        jdbc.update("UPDATE career_player SET wage_eur = NULL WHERE career_id = ? AND player_id = ?", careerId, squad.get(0));
        assertThat(call(get("/api/v1/players/facets?career=" + careerId), null, token, 200).at("/data").has("squadWageEur")).isFalse();
    }

    private long outsider() {
        return jdbc.queryForObject("""
                SELECT cp.player_id FROM career_player cp
                 WHERE cp.career_id = ? AND NOT EXISTS (SELECT 1 FROM user_player_state s WHERE s.career_id = cp.career_id AND s.base_player_id = cp.player_id)
                   AND NOT ('GK' = ANY (cp.positions))
                 ORDER BY cp.player_id LIMIT 1""", Long.class, careerId);
    }

    private String register() throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", "page-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!"), null, 200)
                .at("/data/accessToken").asText();
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
