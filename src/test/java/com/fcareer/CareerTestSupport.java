package com.fcareer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.repository.ClubRepository;
import org.springframework.http.HttpHeaders;
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
    private final ClubRepository clubRepository;

    public CareerTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, ClubRepository clubRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clubRepository = clubRepository;
    }

    public String register() throws Exception {
        JsonNode registered = json(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "sup-" + UUID.randomUUID() + "@example.com", "password", PASSWORD))), null);
        return registered.at("/data/accessToken").asText();
    }

    public long premiumCareer(String token, Long budgetEur) throws Exception {
        var club = clubRepository.findAll().get(0);
        Map<String, Object> body = budgetEur == null ? Map.of("clubId", club.getId()) : Map.of("clubId", club.getId(), "budgetEur", budgetEur);
        long careerId = json(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), token).at("/data/id").asLong();
        json(post("/api/v1/billing/subscribe"), token);
        return careerId;
    }

    public String firstClubName() {
        return clubRepository.findAll().get(0).getName();
    }

    /** players: "playerid,overallrating,potential" satırları; links: "playerid,teamid"; loans: "playerid,teamidloanedfrom" (null olabilir). */
    public JsonNode importCsv(long careerId, String token, String players, String links, String loans) throws Exception {
        var builder = multipart("/api/v1/careers/" + careerId + "/import")
                .file(file("players", "playerid,overallrating,potential\n" + players))
                .file(file("teams", "teamid,teamname,overallrating,gender\n1,\"" + firstClubName() + "\",80,0\n111592,Serbest,60,0\n"))
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
