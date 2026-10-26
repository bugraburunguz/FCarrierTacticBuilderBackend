package com.fcareer.manager;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * MGR-a madde 2 ve 4: menajer profili (kullanıcı başına bir satır, doğrulama, sahiplik) ve kulüp önerisi ucu (6 kategori, tekrar yok, 3 kanıt satırı,
 * beklenti metni; katalog kulüp profili yoksa catalogReady false; uyum önbellekte). Eklenen kulüp profilleri transaction sonunda geri alınır.
 */
@IntegrationTest
@Transactional
class ManagerIntegrationTest {

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
    private ClubFitCalculator fitCalculator;

    private CareerTestSupport support;

    private static final Map<String, Object> PROFILE = Map.of("name", "Buğra", "licence", "NATIONAL_A", "background", "PRO_REGIONAL",
            "style", "COUNTER", "priorities", List.of("YOUTH", "QUICK_SUCCESS"), "difficulty", "MEDIUM");

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private int status(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private JsonNode data(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        var response = mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).at("/data");
    }

    /** Mock katalog kulüplerine (en az 11 katalog oyuncusu olanlar) farklı prestijli kulüp profili. */
    private int seedProfiles() {
        jdbc.update("DELETE FROM club_profile");
        List<Integer> clubs = jdbc.queryForList("""
                SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id JOIN club c ON c.id = r.club_id
                 WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND COALESCE(c.gender, 0) = 0
                 GROUP BY r.club_id HAVING count(*) >= 11 ORDER BY r.club_id""", Integer.class);
        for (int i = 0; i < clubs.size(); i++) {
            jdbc.update("""
                    INSERT INTO club_profile (club_id, game_version_id, domestic_prestige, international_prestige, popularity, club_worth_eur, youth_development, league_level)
                    VALUES (?, 27, ?, ?, 5, ?, ?, 1)""", clubs.get(i), 1 + i % 10, 1 + (i * 3) % 10, 1_000_000L * (i + 1), (i * 7) % 11);
        }
        return clubs.size();
    }

    @Test
    void profileIsPerUserValidatedAndCarriesTheReputation() throws Exception {
        String token = support.register();
        assertThat(status(get("/api/v1/me/manager-profile"), null, token)).as("profil yok").isEqualTo(404);
        assertThat(status(get("/api/v1/me/manager-profile"), null, null)).isEqualTo(401);

        JsonNode saved = data(put("/api/v1/me/manager-profile"), PROFILE, token);
        int rows = jdbc.queryForObject("SELECT count(*) FROM manager_profile", Integer.class);
        assertThat(saved.path("reputation").asInt()).as("bölgesel 40 + Ulusal A 6").isEqualTo(46);
        assertThat(saved.path("band").asText()).isEqualTo("Ulusal tanınırlık");
        assertThat(saved.at("/profile/priorities")).hasSize(2);
        assertThat(data(get("/api/v1/me/manager-profile"), null, token).at("/profile/name").asText()).isEqualTo("Buğra");

        // Düzenleme: aynı satır güncellenir.
        Map<String, Object> edited = new java.util.HashMap<>(PROFILE);
        edited.put("background", "WORLD_CLASS");
        edited.put("formation", "4-4-2");
        assertThat(data(put("/api/v1/me/manager-profile"), edited, token).path("reputation").asInt()).isEqualTo(91);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manager_profile", Integer.class)).as("düzenleme yeni satır açmaz").isEqualTo(rows);

        // Doğrulama: en çok 2 öncelik, tekrarsız; geçersiz enum ve bilinmeyen formasyon 400.
        Map<String, Object> bad = new java.util.HashMap<>(PROFILE);
        bad.put("priorities", List.of("YOUTH", "RESCUE", "FINANCIAL"));
        assertThat(status(put("/api/v1/me/manager-profile"), bad, token)).isEqualTo(400);
        bad.put("priorities", List.of("YOUTH", "YOUTH"));
        assertThat(status(put("/api/v1/me/manager-profile"), bad, token)).isEqualTo(400);
        bad.put("priorities", List.of());
        bad.put("licence", "DOCTORATE");
        assertThat(status(put("/api/v1/me/manager-profile"), bad, token)).isEqualTo(400);
        bad.put("licence", "PRO");
        bad.put("formation", "9-0-1");
        assertThat(status(put("/api/v1/me/manager-profile"), bad, token)).isEqualTo(400);

        // Sahiplik: başka kullanıcı kendi (boş) profilini görür.
        String other = support.register();
        assertThat(status(get("/api/v1/me/manager-profile"), null, other)).isEqualTo(404);
    }

    @Test
    void recommendationsGiveUpToSixDistinctCardsWithEvidenceAndExpectation() throws Exception {
        int clubs = seedProfiles();
        assertThat(clubs).isGreaterThanOrEqualTo(6);
        String token = support.register();
        Map<String, Object> body = Map.of("profile", PROFILE);
        JsonNode result = data(post("/api/v1/manager/club-recommendations"), body, token);
        assertThat(result.path("catalogReady").asBoolean()).isTrue();
        assertThat(result.path("reputation").asInt()).isEqualTo(46);
        JsonNode cards = result.path("cards");
        assertThat(cards.size()).isBetween(1, 6);
        Set<Long> ids = new HashSet<>();
        for (JsonNode card : cards) {
            assertThat(ids.add(card.path("clubId").asLong())).as("aynı kulüp iki kartta olmaz").isTrue();
            assertThat(card.path("evidence")).hasSize(3);
            assertThat(card.path("expectation").asText()).isIn("Yönetim hızlı başarı bekler.", "Makul beklenti, zaman tanınır.",
                    "Yönetim sabırlı ama taraftar şüpheli; ilk sezon kritik.");
            assertThat(card.path("difficulty").asText()).isIn("EASY", "MEDIUM", "HARD");
            assertThat(card.path("style").asText()).isEqualTo("COUNTER");
            assertThat(card.path("formation").asText()).isNotBlank();
            assertThat(card.path("squadFit").isNumber()).isTrue();
        }
        // Öncelik sırası: Gençler, sonra Hızlı başarı (Güvenli) kartları öne gelir (çıktıysa).
        List<String> categories = new java.util.ArrayList<>();
        cards.forEach((c) -> categories.add(c.path("category").asText()));
        List<String> expectedOrder = List.of("YOUTH_PROJECT", "SAFE", "BEST_FIT", "RESCUE", "STRONG_BUDGET", "DREAM");
        assertThat(categories).isSortedAccordingTo(java.util.Comparator.comparingInt(expectedOrder::indexOf));
        assertThat(cards.size() + result.path("missing").size()).as("her kategori ya kart ya gerekçe").isEqualTo(6);
        result.path("missing").forEach((m) -> assertThat(m.path("reason").asText()).isNotBlank());

        // Kayıtlı profille (gövdesiz) de çalışır.
        data(put("/api/v1/me/manager-profile"), PROFILE, token);
        assertThat(data(post("/api/v1/manager/club-recommendations"), Map.of(), token).path("cards").size()).isEqualTo(cards.size());
    }

    @Test
    void fitResultsAreCachedPerClubFormationStyleAndCatalog() throws Exception {
        seedProfiles();
        String token = support.register();
        Map<String, Object> body = Map.of("profile", PROFILE);
        data(post("/api/v1/manager/club-recommendations"), body, token);
        long after = fitCalculator.computations();
        data(post("/api/v1/manager/club-recommendations"), body, token);
        assertThat(fitCalculator.computations()).as("ikinci istek önbellekten").isEqualTo(after);
        Map<String, Object> possession = new java.util.HashMap<>(PROFILE);
        possession.put("style", "POSSESSION");
        data(post("/api/v1/manager/club-recommendations"), Map.of("profile", possession), token);
        assertThat(fitCalculator.computations()).as("tarz değişince yeniden hesap").isGreaterThan(after);
    }

    @Test
    void withoutClubProfilesTheCatalogIsNotReadyAndWithoutAProfileTheRequestFails() throws Exception {
        jdbc.update("DELETE FROM club_profile");
        String token = support.register();
        JsonNode result = data(post("/api/v1/manager/club-recommendations"), Map.of("profile", PROFILE), token);
        assertThat(result.path("catalogReady").asBoolean()).isFalse();
        assertThat(result.path("cards")).isEmpty();
        assertThat(status(post("/api/v1/manager/club-recommendations"), Map.of(), token)).as("kayıtlı profil yok, gövde yok").isEqualTo(404);
        assertThat(status(post("/api/v1/manager/club-recommendations"), Map.of("profile", PROFILE), null)).isEqualTo(401);
    }
}
