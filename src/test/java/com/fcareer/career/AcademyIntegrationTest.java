package com.fcareer.career;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * ACADEMY-a: kariyer içinden alınmış export (fixture: Port Vale + altyapı, live_editor_25-10-2026_in_career.log'dan) içe aktarılınca altyapı takımı veriden
 * bulunur, career.academy_team_id'ye yazılır; altyapı oyuncuları A takım kadrosuna girmez, ayrı uçtan döner; çözülmemiş takım adı hiçbir yerde görünmez;
 * kariyer verisi (oyun tarihi, maaş, sözleşme) dolu olduğu için Masa'nın "Kayıt verisi eksik" bandı kalkar. Üretilmiş oyuncular başka kullanıcıya ve
 * başka kariyere sızmaz.
 */
@IntegrationTest
class AcademyIntegrationTest {

    /** Fixture verisi (yalnız testte; kodda bu kimlikler geçmez). */
    private static final int ACADEMY_TEAM = 112264;

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

    private JsonNode importFixture(String token, long careerId) throws Exception {
        byte[] log = new ClassPathResource("le-export/academy-portvale.log").getInputStream().readAllBytes();
        return support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "academy.log", "text/plain", log)), token);
    }

    private JsonNode data(String token, String path, int status) throws Exception {
        var response = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(path + " " + response.getContentAsString()).isEqualTo(status);
        return status == 200 ? objectMapper.readTree(response.getContentAsString()).at("/data") : null;
    }

    @Test
    void academyIsDetectedNamedAndKeptOutOfTheFirstTeam() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        JsonNode imported = importFixture(token, careerId);
        assertThat(imported.path("success").asBoolean()).as(imported.toString()).isTrue();

        assertThat(jdbc.queryForObject("SELECT academy_team_id FROM career WHERE id = ?", Integer.class, careerId)).isEqualTo(ACADEMY_TEAM);
        JsonNode academy = data(token, "/api/v1/careers/" + careerId + "/academy", 200);
        assertThat(academy.get("found").asBoolean()).isTrue();
        assertThat(academy.get("name").asText()).isEqualTo("Port Vale Youth Academy");
        assertThat(academy.get("players")).hasSize(7);
        JsonNode kyle = null;
        for (JsonNode p : academy.get("players")) {
            if (p.get("name").asText().equals("Kyle Preston")) {
                kyle = p;
            }
        }
        assertThat(kyle).as("ad editedplayernames'ten").isNotNull();
        assertThat(kyle.get("age").asInt()).isEqualTo(15);
        assertThat(kyle.get("overall").asInt()).isEqualTo(55);
        assertThat(kyle.get("potential").asInt()).isEqualTo(73);
        assertThat(kyle.get("monthsInSquad").asInt()).isEqualTo(3);
        assertThat(kyle.get("potentialVariance").asInt()).isEqualTo(5);
        assertThat(kyle.get("youthVerified").asBoolean()).as("anlamı doğrulanmadı").isFalse();

        // A takım kadrosu altyapıyı içermez.
        Set<Long> academyIds = new HashSet<>();
        academy.get("players").forEach((p) -> academyIds.add(p.get("playerId").asLong()));
        JsonNode squad = data(token, "/api/v1/careers/" + careerId + "/squad", 200);
        assertThat(squad.size()).isGreaterThanOrEqualTo(29);
        squad.forEach((m) -> assertThat(academyIds).doesNotContain(m.at("/player/id").asLong()));

        // Çözülmemiş yerelleştirme anahtarı hiçbir takım adında görünmez.
        List<String> names = new ArrayList<>();
        data(token, "/api/v1/careers/" + careerId + "/teams?limit=100", 200).forEach((t) -> names.add(t.path("name").asText()));
        assertThat(names).noneMatch((n) -> n.startsWith("*")).contains("Port Vale Youth Academy");
        assertThat(jdbc.queryForList("SELECT name FROM career_team WHERE career_id = ?", String.class, careerId)).noneMatch((n) -> n.startsWith("*"));

        // Kariyer verisi: oyun tarihi META'dan (career_calendar varsayılan 20080101), maaş ve sözleşme dolu → Masa bandı kalkar.
        JsonNode facets = data(token, "/api/v1/players/facets?career=" + careerId, 200);
        assertThat(facets.get("gameDate").asText()).isEqualTo("2027-09-19");
        assertThat(facets.get("contractData").asBoolean()).isTrue();
        assertThat(facets.get("loanData").asBoolean()).isTrue();
        assertThat(facets.hasNonNull("squadWageEur")).isTrue();

        // Yeniden içe aktarma: aynı 7 oyuncu, tekrar yok.
        importFixture(token, careerId);
        assertThat(data(token, "/api/v1/careers/" + careerId + "/academy", 200).get("players")).hasSize(7);
    }

    @Test
    void scoutRangeModeMasksAcademyPotentialAndUnverifiedPotentialFields() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        importFixture(token, careerId);
        var patch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/careers/" + careerId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"spoilerMode\":\"SCOUT_RANGE\"}");
        support.json(patch, token);

        JsonNode academy = data(token, "/api/v1/careers/" + careerId + "/academy", 200);
        assertThat(academy.get("players")).hasSize(7);
        for (JsonNode p : academy.get("players")) {
            assertThat(p.get("potential").isNull()).as("gerçek potansiyel dönmez").isTrue();
            assertThat(p.get("potentialRange").asText()).matches("\\d+–\\d+");
            // Anlamı doğrulanmamış potansiyel alanları bantla birleşince tam değeri verebilir: aralık modunda dönmez.
            assertThat(p.get("potentialVariance").isNull()).isTrue();
            assertThat(p.get("swingLowPotential").isNull()).isTrue();
        }
    }

    @Test
    void generatedAcademyPlayersDoNotLeakToOtherUsersOrCareers() throws Exception {
        String owner = support.register();
        long careerId = support.premiumCareer(owner, null);
        importFixture(owner, careerId);

        String stranger = support.register();
        data(stranger, "/api/v1/careers/" + careerId + "/academy", 404);

        long other = support.premiumCareer(owner, null);
        JsonNode empty = data(owner, "/api/v1/careers/" + other + "/academy", 200);
        assertThat(empty.get("found").asBoolean()).isFalse();
        assertThat(empty.get("message").asText()).isEqualTo("Altyapıda oyuncu yok ya da export'ta görünmüyor");
        assertThat(empty.get("players")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_youth_player WHERE career_id = ?", Integer.class, other)).isZero();

        // Altyapı takımı katalog dışı (yer tutucu) ligde: kariyer içe aktarma global katalogda kulüp ya da lig açmaz.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club WHERE ea_team_id = ?", Integer.class, ACADEMY_TEAM)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM league WHERE ea_league_id = 2028 OR name = 'DÜNYA LİGİ'", Integer.class)).isZero();
    }
}
