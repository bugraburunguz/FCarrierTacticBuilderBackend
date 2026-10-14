package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * E3-2 eşitlik: eski okuma (katalog player_rating + career_player_data/career_player_rating overlay'i) ile yeni tek kaynak career_player
 * oyuncu sayısı, OVR, pozisyon ve 34 attribute'ta birebir aynı. Eski okuma yalnız bu testte yaşar.
 */
@IntegrationTest
class CareerPlayerEquivalenceIntegrationTest {

    private static final String PASSWORD = "S3cure-pass!";
    private static final long FIRST_MOCK_ID = 900_000L;
    private static final long GENERATED_ID = 999_600L;
    private static final short GAME_VERSION = 27;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private static String legacyAttrs() {
        return Arrays.stream(Attr.values()).map((a) -> "COALESCE(cpr." + a.getColumn() + ", r." + a.getColumn() + ")").collect(Collectors.joining(", "));
    }

    /** Eski okuma yolunun (PlayerSearchRepository) özü: katalog satırı + varsa kariyer overlay'i. */
    private List<Map<String, Object>> legacyRows(long careerId) {
        return jdbc.queryForList("SELECT b.id AS id, COALESCE(cpd.overall, r.overall) AS ovr, array_to_string(COALESCE(cpr.positions, r.positions), ',') AS positions, "
                + "ARRAY[" + legacyAttrs() + "]::text AS attrs "
                + "FROM player_rating r JOIN base_player b ON b.id = r.base_player_id "
                + "LEFT JOIN career_player_data cpd ON cpd.base_player_id = r.base_player_id AND cpd.career_id = ? "
                + "LEFT JOIN career_player_rating cpr ON cpr.base_player_id = r.base_player_id AND cpr.career_id = cpd.career_id AND cpr.game_version_id = r.game_version_id "
                + "WHERE r.game_version_id = ? AND (b.owner_career_id IS NULL OR b.owner_career_id = ?) AND b.retired_at IS NULL ORDER BY b.id", careerId, GAME_VERSION, careerId);
    }

    private List<Map<String, Object>> newRows(long careerId) {
        return jdbc.queryForList("SELECT player_id AS id, ovr, array_to_string(positions, ',') AS positions, attrs::text AS attrs FROM career_player WHERE career_id = ? ORDER BY player_id", careerId);
    }

    private void assertEquivalent(long careerId) {
        List<Map<String, Object>> legacy = legacyRows(careerId);
        List<Map<String, Object>> current = newRows(careerId);
        assertThat(current).as("oyuncu sayısı").hasSameSizeAs(legacy).isNotEmpty();
        for (int i = 0; i < legacy.size(); i++) {
            assertThat(current.get(i).get("id")).isEqualTo(legacy.get(i).get("id"));
            assertThat(current.get(i).get("ovr")).as("ovr " + legacy.get(i).get("id")).isEqualTo(legacy.get(i).get("ovr"));
            assertThat(current.get(i).get("positions")).as("pozisyon " + legacy.get(i).get("id")).isEqualTo(legacy.get(i).get("positions"));
            assertThat(current.get(i).get("attrs")).as("attribute " + legacy.get(i).get("id")).isEqualTo(legacy.get(i).get("attrs"));
        }
    }

    @Test
    void csvImportKeepsCareerPlayerEqualToTheLegacyRead() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        MockHttpServletRequestBuilder upload = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n" + (FIRST_MOCK_ID + 2) + ",77,80\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n111592,Serbest,60,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n" + (FIRST_MOCK_ID + 2) + ",111592\n"));
        assertThat(json(upload, token).at("/success").asBoolean()).isTrue();
        assertEquivalent(careerId);
        assertThat(jdbc.queryForObject("SELECT data_version FROM career WHERE id = ?", Long.class, careerId)).as("her içe aktarma data_version'ı artırır").isPositive();
    }

    @Test
    void liveEditorLogImportWithOverlayAttributesAndAGeneratedPlayerStaysEqual() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        String tab = "\t";
        String prefix = "10:00:00.1" + tab + "INFO" + tab + "[LUA API] FCDUMP" + tab;
        String log = String.join("\n",
                prefix + "BEGIN" + tab + "players" + tab + "fields=3",
                prefix + "HEADER" + tab + "players" + tab + "playerid,overallrating,potential,preferredposition1,gender,agility,trait1,icontrait1,role1",
                prefix + "ROW" + tab + "players" + tab + FIRST_MOCK_ID + ",88,92,14,0,70,2097152,2097152,141",
                prefix + "ROW" + tab + "players" + tab + GENERATED_ID + ",66,84,25,0,70,0,0,0",
                prefix + "END" + tab + "players" + tab + "2",
                prefix + "BEGIN" + tab + "teams" + tab + "fields=3",
                prefix + "HEADER" + tab + "teams" + tab + "teamid,teamname,overallrating",
                prefix + "ROW" + tab + "teams" + tab + "1,\"" + club + "\",80",
                prefix + "BEGIN" + tab + "teamplayerlinks" + tab + "fields=2",
                prefix + "HEADER" + tab + "teamplayerlinks" + tab + "playerid,teamid",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + FIRST_MOCK_ID + ",1",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + GENERATED_ID + ",1");
        assertThat(json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", log)), token).at("/success").asBoolean()).isTrue();
        assertEquivalent(careerId);
        assertThat(jdbc.queryForObject("SELECT playstyles::text FROM career_player WHERE career_id = ? AND player_id = (SELECT id FROM base_player WHERE ea_resource_id = ?)", String.class, careerId, FIRST_MOCK_ID))
                .as("trait1/icontrait1 bit 21 → Rapid PS+").contains("Rapid").contains("2");
        assertThat(jdbc.queryForObject("SELECT role_familiarity::text FROM career_player WHERE career_id = ? AND player_id = (SELECT id FROM base_player WHERE ea_resource_id = ?)", String.class, careerId, FIRST_MOCK_ID))
                .as("role1=141 → ST Advanced Forward++ (41, 2)").contains("\"41\"");
    }

    @Test
    void contractYearWageAndLoanEndFromTheLiveEditorExportLandOnCareerPlayer() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        String tab = "\t";
        String prefix = "10:00:00.1" + tab + "INFO" + tab + "[LUA API] FCDUMP" + tab;
        String log = String.join("\n",
                prefix + "BEGIN" + tab + "players" + tab + "fields=5",
                prefix + "HEADER" + tab + "players" + tab + "playerid,overallrating,potential,contractvaliduntil,wage",
                prefix + "ROW" + tab + "players" + tab + FIRST_MOCK_ID + ",88,92,2028,120000",
                prefix + "ROW" + tab + "players" + tab + (FIRST_MOCK_ID + 1) + ",80,85,2027,",
                prefix + "END" + tab + "players" + tab + "2",
                prefix + "BEGIN" + tab + "teams" + tab + "fields=3",
                prefix + "HEADER" + tab + "teams" + tab + "teamid,teamname,overallrating",
                prefix + "ROW" + tab + "teams" + tab + "1,\"" + club + "\",80",
                prefix + "BEGIN" + tab + "teamplayerlinks" + tab + "fields=2",
                prefix + "HEADER" + tab + "teamplayerlinks" + tab + "playerid,teamid",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + FIRST_MOCK_ID + ",1",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + (FIRST_MOCK_ID + 1) + ",1",
                prefix + "BEGIN" + tab + "playerloans" + tab + "fields=3",
                prefix + "HEADER" + tab + "playerloans" + tab + "playerid,teamidloanedfrom,loandateend",
                prefix + "ROW" + tab + "playerloans" + tab + (FIRST_MOCK_ID + 1) + ",2,161330");
        assertThat(json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", log)), token).at("/success").asBoolean()).isTrue();
        String sql = "SELECT %s::text FROM career_player WHERE career_id = ? AND player_id = (SELECT id FROM base_player WHERE ea_resource_id = ?)";
        assertThat(jdbc.queryForObject(String.format(sql, "contract_until"), String.class, careerId, FIRST_MOCK_ID)).as("sezon sonu (30 Haziran) varsayımı").isEqualTo("2028-06-30");
        assertThat(jdbc.queryForObject(String.format(sql, "wage_eur"), String.class, careerId, FIRST_MOCK_ID)).isEqualTo("120000");
        assertThat(jdbc.queryForObject(String.format(sql, "wage_eur"), String.class, careerId, FIRST_MOCK_ID + 1)).as("maaş boşsa bilinmiyor").isNull();
        assertThat(jdbc.queryForObject(String.format(sql, "loan_until"), String.class, careerId, FIRST_MOCK_ID + 1)).as("1582-10-14 + 161330 gün").isEqualTo("2024-06-28");
        assertThat(jdbc.queryForObject(String.format(sql, "loan_until"), String.class, careerId, FIRST_MOCK_ID)).isNull();
    }

    @Test
    void aCareerWithoutAnImportIsSeededFromTheCatalogOnFirstRead() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        JsonNode page = json(get("/api/v1/players?career=" + careerId + "&size=5"), token);
        assertThat(page.at("/success").asBoolean()).as(page.toString()).isTrue();
        assertEquivalent(careerId);
    }

    @Test
    void manualRatingUpdatesReachCareerPlayerToo() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        MockHttpServletRequestBuilder upload = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n"));
        json(upload, token);
        json(post("/api/v1/careers/" + careerId + "/import/squad?teamId=1"), token);
        long playerId = jdbc.queryForObject("SELECT id FROM base_player WHERE ea_resource_id = ?", Long.class, FIRST_MOCK_ID);
        JsonNode updated = json(put("/api/v1/careers/" + careerId + "/players/ratings").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("updates", List.of(Map.of("playerId", playerId, "overall", 93, "potential", 96))))), token);
        assertThat(updated.at("/success").asBoolean()).as(updated.toString()).isTrue();
        assertThat(jdbc.queryForObject("SELECT ovr FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, playerId)).isEqualTo(93);
        assertThat(jdbc.queryForObject("SELECT pot FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, playerId)).isEqualTo(96);
        assertEquivalent(careerId);
    }

    /** E3-4b ön kontrol: LE serbest oyuncu havuzundaki oyuncu katalog kulübüne düşmez; kulüp boş, ad kariyer takımından ("Serbest"). Canlı veride 231/224 böyle oyuncu var, hepsi havuz. */
    @Test
    void aLiveFreeAgentNeverFallsBackToACatalogClub() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        MockHttpServletRequestBuilder upload = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + (FIRST_MOCK_ID + 2) + ",77,80\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n111592,Serbest,60,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + (FIRST_MOCK_ID + 2) + ",111592\n"));
        json(upload, token);
        assertThat(jdbc.queryForObject("SELECT club_id FROM career_player WHERE career_id = ? AND live AND free_agent", Integer.class, careerId)).isNull();
        JsonNode agents = json(get("/api/v1/players?career=" + careerId + "&free_agent=true"), token);
        assertThat(agents.at("/data/items/0/club").asText()).isEqualTo("Serbest");
        assertThat(agents.at("/data/items/0/clubId").isNull() || agents.at("/data/items/0/clubId").isMissingNode()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player cp LEFT JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id "
                + "WHERE cp.career_id = ? AND cp.live AND cp.club_id IS NULL AND ct.team_id IS NULL", Integer.class, careerId)).as("kulübü ve takım adı olmayan live oyuncu yok").isZero();
    }

    private long premiumCareer(String token) throws Exception {
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token).at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);
        return careerId;
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private String register() throws Exception {
        JsonNode registered = json(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "eq-" + UUID.randomUUID() + "@example.com", "password", PASSWORD))), null);
        return registered.at("/data/accessToken").asText();
    }

    private JsonNode json(MockHttpServletRequestBuilder builder, String token) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        String content = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
