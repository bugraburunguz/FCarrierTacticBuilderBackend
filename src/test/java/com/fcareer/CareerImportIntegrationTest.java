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
                prefix + "ROW" + tab + "players" + tab + FIRST_MOCK_ID + ",88,92,-1,0,70",
                prefix + "ROW" + tab + "players" + tab + GENERATED_ID + ",66,84,25,0,70",
                prefix + "END" + tab + "players" + tab + "2",
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
                .content(objectMapper.writeValueAsString(Map.of("email", "imp-" + UUID.randomUUID() + "@example.com", "password", PASSWORD))), null);
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
