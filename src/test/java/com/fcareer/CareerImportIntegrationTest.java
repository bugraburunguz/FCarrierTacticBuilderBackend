package com.fcareer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class CareerImportIntegrationTest {

    private static final String PASSWORD = "S3cure-pass!";
    private static final long FIRST_MOCK_ID = 900_000L;
    private static final long GENERATED_ID = 999_500L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void importRequiresPremiumThenOverlaysRatingsFreeAgentsAndTeamProfile() throws Exception {
        String token = register();
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token)
                .at("/data/id").asLong();

        JsonNode denied = upload(careerId, token, club.getName());
        assertThat(denied.at("/success").asBoolean()).isFalse();

        json(post("/api/v1/billing/subscribe"), token);
        JsonNode result = upload(careerId, token, club.getName());
        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
        assertThat(result.at("/data/players").asInt()).isEqualTo(3);
        assertThat(result.at("/data/freeAgents").asInt()).isEqualTo(1);
        assertThat(result.at("/data/guessedTeamId").asInt()).isEqualTo(1);

        JsonNode agents = json(get("/api/v1/players?career=" + careerId + "&free_agent=true"), token);
        assertThat(agents.at("/data/items")).hasSize(1);
        assertThat(agents.at("/data/items/0/overall").asInt()).isEqualTo(77);
        assertThat(agents.at("/data/items/0/club").asText()).isEqualTo("Serbest");

        JsonNode stranger = json(get("/api/v1/players?career=" + careerId + "&free_agent=true"), register());
        assertThat(stranger.at("/success").asBoolean()).isFalse();
        // REVIEW: "bulunamadı" (404) kodu; 400/500 de success=false verir.
        assertThat(stranger.at("/error/0/code").asText()).isEqualTo("fc.exception.0001");

        JsonNode synced = json(post("/api/v1/careers/" + careerId + "/import/squad?teamId=1"), token);
        assertThat(synced.at("/data/players").asInt()).isEqualTo(2);
        JsonNode squad = json(get("/api/v1/careers/" + careerId + "/squad"), token);
        assertThat(squad.at("/data")).hasSize(2);
        assertThat(squad.at("/data/0/player/overall").asInt()).isIn(91, 90);
        assertThat(squad.at("/data/0/dynamicPotential").asInt()).isIn(95, 93);

        JsonNode profile = json(get("/api/v1/careers/" + careerId + "/teams/1"), token);
        assertThat(profile.at("/data/team/name").asText()).isEqualTo(club.getName());
        assertThat(profile.at("/data/weaknesses").toString()).contains("Savunma");
        assertThat(profile.at("/data/topPlayers")).hasSize(2);
        assertThat(json(get("/api/v1/careers/" + careerId + "/teams?q=Serbest"), token).at("/data")).isEmpty();
    }

    @Test
    void snapshotsKeepPositionsAttributesAndAgeAndAreNeverPrunedByDefault() throws Exception {
        String token = register();
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token)
                .at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);

        for (int i = 0; i < 33; i++) {
            assertThat(upload(careerId, token, club.getName()).at("/success").asBoolean()).isTrue();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_snapshot WHERE career_id = ?", Integer.class, careerId)).as("30'dan eski snapshot silinmez").isEqualTo(33);
        var row = jdbc.queryForMap("""
                SELECT p.positions, p.attrs, p.age FROM career_snapshot_player p
                JOIN career_snapshot s ON s.id = p.snapshot_id WHERE s.career_id = ? ORDER BY s.id DESC LIMIT 1""", careerId);
        assertThat(row.get("positions")).isNotNull();
        assertThat(row.get("attrs").toString()).contains("finishing");
    }

    @Test
    void secondImportKeepsAManuallyBuiltSquad() throws Exception {
        String token = register();
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token)
                .at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);

        assertThat(upload(careerId, token, club.getName()).at("/data/squadSynced").asInt()).as("ilk import kadroyu kurar").isEqualTo(2);

        long manual = jdbc.queryForObject("SELECT id FROM base_player WHERE owner_career_id IS NULL AND id NOT IN (SELECT base_player_id FROM user_player_state WHERE career_id = ?) LIMIT 1", Long.class, careerId);
        jdbc.update("INSERT INTO user_player_state (career_id, base_player_id, club_id, acquired_at, updated_at) VALUES (?, ?, ?, now(), now())", careerId, manual, club.getId());
        jdbc.update("INSERT INTO roster_event (career_id, base_player_id, type, fee_eur) VALUES (?, ?, 'BUY', 1000)", careerId, manual);

        JsonNode second = upload(careerId, token, club.getName());

        assertThat(second.at("/data/squadSynced").isMissingNode() || second.at("/data/squadSynced").isNull()).as("elle düzenlenmiş kadro yeniden kurulmaz").isTrue();
        assertThat(second.at("/data/guessedTeamId").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ? AND base_player_id = ?", Integer.class, careerId, manual)).isEqualTo(1);
    }

    private long premiumCareer(String token) throws Exception {
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token).at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);
        return careerId;
    }

    @Test
    void csvPathAcceptsLoansAndTheNameTablesNotJustTheFiveBaseTables() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n99,Baska,70,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n"))
                .file(file("playerloans", "playerid,teamidloanedfrom\n" + FIRST_MOCK_ID + ",99\n"))
                .file(file("playernames", "nameid,name\n1,Ad\n")), token);

        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ? AND on_loan", Integer.class, careerId))
                .as("playerloans CSV'si artık okunuyor").isEqualTo(1);
    }

    @Test
    void importWarnsWhenGeneratedCareerPlayersHaveNoPositionColumns() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n460000,60,80\n460001,61,82\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n460000,1\n460001,1\n")), token);

        assertThat(result.at("/data/skippedNoPosition").asInt()).isEqualTo(2);
        assertThat(result.at("/data/warnings").toString()).contains("CAREER_PLAYERS_SKIPPED_NO_POSITIONS");
    }

    @Test
    void importWarnsWhenTheExportWasNotTakenInsideACareer() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n"))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + club + "\",80,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n"))
                .file(file("context", "incareer,savetype\n0,0\n")), token);

        assertThat(result.at("/data/warnings").toString()).contains("NOT_IN_CAREER");
    }

    private static String fnv(java.util.List<String> rows) {
        long h = 2166136261L;
        for (String row : rows) {
            for (byte b : row.getBytes(StandardCharsets.UTF_8)) {
                h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
            }
        }
        return String.format("fnv1a32:%08x", h);
    }

    private static void fcx2Table(java.util.List<String> log, String name, String header, java.util.List<String> rows) {
        String prefix = "10:00:00.1\tINFO\t[LUA API] FCX2\t";
        log.add(prefix + "HEADER\t" + name + "\t" + header);
        log.add(prefix + "CHUNK\t" + name + "\t1\t[" + String.join(",", rows) + "]");
        log.add(prefix + "END\t" + name + "\t" + rows.size() + "\t" + fnv(rows) + "\t1");
    }

    private String fcx2Log(String clubName, boolean inCareer, String gameDate, int userTeamId) {
        return fcx2Log(clubName, inCareer, gameDate, userTeamId, "[\"playerid\",\"overallrating\",\"potential\"]",
                java.util.List.of("[" + FIRST_MOCK_ID + ",91,95]", "[" + (FIRST_MOCK_ID + 1) + ",90,93]"));
    }

    private String fcx2Log(String clubName, boolean inCareer, String gameDate, int userTeamId, String playersHeader, java.util.List<String> players) {
        String prefix = "10:00:00.1\tINFO\t[LUA API] FCX2\t";
        java.util.List<String> log = new java.util.ArrayList<>();
        log.add(prefix + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.0\",\"context\":{\"inCareer\":" + inCareer
                + ",\"mode\":\"MANAGER\",\"gameDate\":\"" + gameDate + "\",\"userTeamId\":" + userTeamId + "}}");
        fcx2Table(log, "players", playersHeader, players);
        fcx2Table(log, "teams", "[\"teamid\",\"teamname\",\"overallrating\",\"gender\"]", java.util.List.of("[1,\"" + clubName + "\",80,0]", "[77,\"Baska FC\",70,0]"));
        fcx2Table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", java.util.List.of("[" + FIRST_MOCK_ID + ",1]", "[" + (FIRST_MOCK_ID + 1) + ",77]"));
        log.add(prefix + "DONE\t[\"MISSINGTABLE career_users\"]");
        return String.join("\n", log);
    }

    @Test
    void v2ExportDrivesTheUserTeamTheGameDateAndTheSnapshot() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", fcx2Log(club, true, "2029-07-01", 77))), token);

        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
        assertThat(result.at("/data/guessedTeamId").asInt()).as("takım adla tahmin edilmez, context.userTeamId kullanılır").isEqualTo(77);
        assertThat(result.at("/data/squadSynced").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_game_date FROM career WHERE id = ?", java.time.LocalDate.class, careerId)).isEqualTo(java.time.LocalDate.of(2029, 7, 1));
        assertThat(jdbc.queryForObject("SELECT game_date FROM career_snapshot WHERE career_id = ? ORDER BY id DESC LIMIT 1", java.time.LocalDate.class, careerId)).isEqualTo(java.time.LocalDate.of(2029, 7, 1));
        assertThat(jdbc.queryForObject("SELECT note FROM career_snapshot WHERE career_id = ? ORDER BY id DESC LIMIT 1", String.class, careerId)).contains("export ");
        // FAZ 6a-A: özet neyin geldiğini söyler; maaş/sözleşme kolonu olmayan export'ta açık uyarı, betiğin ham MISSINGTABLE satırı kullanıcıya gitmez.
        assertThat(result.at("/data/gameDate").asText()).isEqualTo("2029-07-01");
        assertThat(result.at("/data/contracts").asInt()).isZero();
        assertThat(result.at("/data/warnings").toString()).contains("CONTRACTS_MISSING").doesNotContain("MISSINGTABLE");
    }

    @Test
    void v2ExportWithWagesAndContractsReportsThemAndDoesNotWarn() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();
        String log = fcx2Log(club, true, "2029-07-01", 77, "[\"playerid\",\"overallrating\",\"potential\",\"wage\",\"contractvaliduntil\"]",
                java.util.List.of("[" + FIRST_MOCK_ID + ",91,95,11000,2030]", "[" + (FIRST_MOCK_ID + 1) + ",90,93,6500,2029]"));

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", log)), token);

        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
        assertThat(result.at("/data/contracts").asInt()).isEqualTo(2);
        assertThat(result.at("/data/warnings").toString()).doesNotContain("CONTRACTS_MISSING");
    }

    @Test
    void v2ExportTakenOutsideACareerWarnsAndCorruptExportsAreRejected() throws Exception {
        String token = register();
        long careerId = premiumCareer(token);
        String club = clubRepository.findAll().get(0).getName();

        JsonNode warned = json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", fcx2Log(club, false, "2029-07-01", 0))), token);
        assertThat(warned.at("/data/warnings").toString()).contains("NOT_IN_CAREER");

        JsonNode corrupt = json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", fcx2Log(club, true, "2029-07-01", 77).replace("[" + FIRST_MOCK_ID + ",91,95]", "[" + FIRST_MOCK_ID + ",99,95]"))), token);
        assertThat(corrupt.at("/success").asBoolean()).isFalse();
        assertThat(corrupt.at("/message").asText() + corrupt.at("/error").toString()).contains("players");
    }

    @Test
    void importAcceptsTheLiveEditorLogFileDirectly() throws Exception {
        String token = register();
        var club = clubRepository.findAll().get(0);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clubId", club.getId(), "budgetEur", 1_000_000))), token)
                .at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);

        String tab = "\t";
        String prefix = "10:00:00.1" + tab + "INFO" + tab + "[LUA API] FCDUMP" + tab;
        String log = String.join("\n",
                prefix + "BEGIN" + tab + "players" + tab + "fields=3",
                prefix + "HEADER" + tab + "players" + tab + "playerid,overallrating,potential,preferredposition1,gender,agility",
                prefix + "ROW" + tab + "players" + tab + FIRST_MOCK_ID + ",88,92,14,0,70",
                prefix + "ROW" + tab + "players" + tab + GENERATED_ID + ",66,84,25,0,70",
                prefix + "END" + tab + "players" + tab + "2",
                prefix + "BEGIN" + tab + "playerloans" + tab + "fields=2",
                prefix + "HEADER" + tab + "playerloans" + tab + "playerid,teamidloanedfrom",
                prefix + "ROW" + tab + "playerloans" + tab + FIRST_MOCK_ID + ",99",
                prefix + "END" + tab + "playerloans" + tab + "1",
                prefix + "BEGIN" + tab + "teams" + tab + "fields=3",
                prefix + "HEADER" + tab + "teams" + tab + "teamid,teamname,overallrating",
                prefix + "ROW" + tab + "teams" + tab + "1,\"" + club.getName() + "\",80",
                prefix + "BEGIN" + tab + "teamplayerlinks" + tab + "fields=2",
                prefix + "HEADER" + tab + "teamplayerlinks" + tab + "playerid,teamid",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + FIRST_MOCK_ID + ",1",
                prefix + "ROW" + tab + "teamplayerlinks" + tab + GENERATED_ID + ",1");

        JsonNode result = json(multipart("/api/v1/careers/" + careerId + "/import").file(file("log", log)), token);

        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
        assertThat(result.at("/data/players").asInt()).isEqualTo(2);
        assertThat(result.at("/data/generatedPlayers").asInt()).isEqualTo(1);
        assertThat(result.at("/data/squadSynced").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = ? AND on_loan", Integer.class, careerId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT array_to_string(positions, ',') FROM career_player_rating WHERE career_id = ? AND overall = 88", String.class, careerId))
                .containsExactly("CM");
        assertThat(result.at("/data/guessedTeamId").asInt()).isEqualTo(1);

        String name = String.valueOf(GENERATED_ID);
        assertThat(json(get("/api/v1/players?q=" + name), null).at("/data/items")).isEmpty();
        JsonNode own = json(get("/api/v1/players?career=" + careerId + "&q=" + name), token);
        assertThat(own.at("/data/items")).hasSize(1);
        assertThat(own.at("/data/items/0/overall").asInt()).isEqualTo(66);
        assertThat(own.at("/data/items/0/potential").asInt()).isEqualTo(84);

        json(post("/api/v1/careers/" + careerId + "/import/squad?teamId=1"), token);
        JsonNode squad = json(get("/api/v1/careers/" + careerId + "/squad"), token);
        assertThat(squad.at("/data")).hasSize(2);

        JsonNode deleted = json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/careers/" + careerId), token);
        assertThat(deleted.at("/success").asBoolean()).as(deleted.toString()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM base_player WHERE career_source_id = ?", Integer.class, GENERATED_ID)).isZero();
    }

    private JsonNode upload(long careerId, String token, String clubName) throws Exception {
        MockHttpServletRequestBuilder builder = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n"
                        + (FIRST_MOCK_ID + 2) + ",77,80\n"))
                .file(file("teams", "teamid,teamname,overallrating,attackrating,midfieldrating,defenserating,gender\n1,\"" + clubName
                        + "\",80,86,80,72,0\n111592,Serbest,60,60,60,60,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n"
                        + (FIRST_MOCK_ID + 2) + ",111592\n"));
        return json(builder, token);
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private String register() throws Exception {
        JsonNode registered = json(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "imp-" + UUID.randomUUID() + "@example.com", "password", PASSWORD, "privacyVersion", 1))), null);
        return registered.at("/data/accessToken").asText();
    }

    private JsonNode json(MockHttpServletRequestBuilder builder, String token) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
