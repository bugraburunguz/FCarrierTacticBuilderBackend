package com.fcareer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Entegrasyon testleri için kariyer kurulumu: kayıt, premium kariyer ve CSV içe aktarma (mock katalog oyuncuları 900000+). */
public final class CareerTestSupport {

    public static final long FIRST_MOCK_ID = 900_000L;
    private static final String PASSWORD = "S3cure-pass!";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public CareerTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    /**
     * Test kariyerinin kulübü: sahte katalogun ("Mock FC n", erkek) katalog kadrosu en kalabalık kulübü, eşitlikte küçük id.
     * {@code findAll().get(0)} sırasızdır; Postgres'te başka testler kulüp satırını güncelleyince ilk sıraya kadrosuz kulüp gelebilir.
     * Yalnız sahte katalog kulüpleri sayılır: başka testlerin içe aktardığı fikstür kulüplerinin oyuncuları FIRST_MOCK_ID eşlemesine uymaz.
     * İçe aktarmanın ürettiği (owner_career_id) oyuncular sayılmaz.
     */
    public static int catalogClubId(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id JOIN club c ON c.id = r.club_id "
                + "WHERE r.game_version_id = 27 AND b.owner_career_id IS NULL AND c.name LIKE 'Mock FC %' AND c.gender = 0 "
                + "GROUP BY r.club_id ORDER BY count(*) DESC, r.club_id LIMIT 1", Integer.class);
    }

    public static String catalogClubName(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT name FROM club WHERE id = ?", String.class, catalogClubId(jdbc));
    }

    public String register() throws Exception {
        JsonNode registered = json(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "sup-" + UUID.randomUUID() + "@example.com", "password", PASSWORD, "privacyVersion", 1))), null);
        return registered.at("/data/accessToken").asText();
    }

    public long premiumCareer(String token, Long budgetEur) throws Exception {
        int clubId = catalogClubId(jdbc);
        Map<String, Object> body = budgetEur == null ? Map.of("clubId", clubId) : Map.of("clubId", clubId, "budgetEur", budgetEur);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), token).at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);
        return careerId;
    }

    public String clubName() {
        return catalogClubName(jdbc);
    }

    /** players: "playerid,overallrating,potential" satırları; links: "playerid,teamid"; loans: "playerid,teamidloanedfrom" (null olabilir). */
    public JsonNode importCsv(long careerId, String token, String players, String links, String loans) throws Exception {
        var builder = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + players))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + clubName() + "\",80,0\n111592,Serbest,60,0\n"))
                .file(file("teamplayerlinks", "playerid,teamid\n" + links));
        if (loans != null) {
            builder.file(file("playerloans", "playerid,teamidloanedfrom\n" + loans));
        }
        return json(builder, token);
    }

    public JsonNode json(MockHttpServletRequestBuilder builder, String token) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        String content = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
