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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Betik 2.0.2: kariyerde yapılan kiralıklar playerloans'ın ilk kopyasında yok. Kiralık kaynakları birleşir: playerloans → diğer kopyalar
 * (playerloans_db*) → kadro durumu (fca_squadstatus loanedout=1; kaynak kulüp ana kayıttaki kulüp). Bağlamdaki transferBudget kariyer bütçesi olur.
 */
@IntegrationTest
class CareerLoanSourcesIntegrationTest {

    private static final String PREFIX = "10:00:00.1\tINFO\t[LUA API] FCX2\t";

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

    private static String fnv(List<String> rows) {
        long h = 2166136261L;
        for (String row : rows) {
            for (byte b : row.getBytes(StandardCharsets.UTF_8)) {
                h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
            }
        }
        return String.format("fnv1a32:%08x", h);
    }

    private static void table(List<String> log, String name, String header, List<String> rows) {
        log.add(PREFIX + "HEADER\t" + name + "\t" + header);
        if (!rows.isEmpty()) {
            log.add(PREFIX + "CHUNK\t" + name + "\t1\t[" + String.join(",", rows) + "]");
        }
        log.add(PREFIX + "END\t" + name + "\t" + rows.size() + "\t" + fnv(rows) + "\t" + (rows.isEmpty() ? 0 : 1));
    }

    private int eaTeamId(long clubId) {
        Integer current = jdbc.queryForObject("SELECT ea_team_id FROM club WHERE id = ?", Integer.class, clubId);
        if (current != null) {
            return current;
        }
        int assigned = 7_100_000 + (int) clubId;
        jdbc.update("UPDATE club SET ea_team_id = ? WHERE id = ?", assigned, clubId);
        return assigned;
    }

    @Test
    void loansFromOtherTableCopiesAndTheSquadStatusAreMergedAndTheBudgetIsRead() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        var us = clubRepository.findAll().get(0);
        long otherClubId = jdbc.queryForObject("""
                SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id AND b.owner_career_id IS NULL
                 WHERE r.game_version_id = 27 AND r.club_id <> ? GROUP BY r.club_id HAVING count(*) >= 3
                 ORDER BY r.club_id DESC LIMIT 1""", Long.class, us.getId());
        String otherName = jdbc.queryForObject("SELECT name FROM club WHERE id = ?", String.class, otherClubId);
        List<Long> theirs = jdbc.queryForList("""
                SELECT b.ea_resource_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.club_id = ? AND r.game_version_id = 27 AND b.owner_career_id IS NULL ORDER BY b.id LIMIT 3""", Long.class, otherClubId);
        int usTeam = eaTeamId(us.getId());
        int otherTeam = eaTeamId(otherClubId);
        long fromCopy = theirs.get(0);
        long fromSquadStatus = theirs.get(1);
        long bought = theirs.get(2);

        List<String> log = new ArrayList<>();
        log.add(PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.2\",\"context\":{\"inCareer\":true,\"mode\":\"MANAGER\","
                + "\"gameDate\":\"2026-09-12\",\"userTeamIdMem\":" + usTeam + ",\"transferBudget\":12345678}}");
        table(log, "players", "[\"playerid\",\"overallrating\",\"potential\"]", List.of("[" + fromCopy + ",70,75]", "[" + fromSquadStatus + ",71,76]", "[" + bought + ",72,77]"));
        table(log, "teams", "[\"teamid\",\"teamname\"]", List.of("[" + usTeam + ",\"" + us.getName() + "\"]", "[" + otherTeam + ",\"" + otherName + "\"]"));
        table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[" + fromCopy + "," + usTeam + "]", "[" + fromSquadStatus + "," + usTeam + "]", "[" + bought + "," + usTeam + "]"));
        table(log, "playerloans", "[\"playerid\",\"teamidloanedfrom\"]", List.of());
        table(log, "playerloans_db3", "[\"playerid\",\"teamidloanedfrom\"]", List.of("[" + fromCopy + "," + otherTeam + "]"));
        table(log, "fca_squadstatus", "[\"playerid\",\"loanedout\",\"presigned\"]", List.of("[" + fromCopy + ",0,0]", "[" + fromSquadStatus + ",1,0]", "[" + bought + ",0,0]"));
        log.add(PREFIX + "DBMAP\t1\t{\"players\":3}");
        log.add(PREFIX + "DONE\t[]");
        var file = new MockMultipartFile("log", "live_editor.log", "text/plain", String.join("\n", log).getBytes(StandardCharsets.UTF_8));
        support.json(multipart("/api/v1/careers/" + careerId + "/import").file(file), token);

        JsonNode moves = support.json(get("/api/v1/careers/" + careerId + "/moves").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null).at("/data");
        Map<Long, JsonNode> byEa = new HashMap<>();
        StreamSupport.stream(moves.spliterator(), false).forEach(m -> byEa.put(m.at("/eaId").asLong(), m));
        assertThat(byEa.get(fromCopy).at("/type").asText()).isEqualTo("LOAN_IN");
        assertThat(byEa.get(fromCopy).at("/fromClub").asText()).isEqualTo(otherName);
        assertThat(byEa.get(fromSquadStatus).at("/type").asText()).as("kadro durumu: kiralıkta, kaynak ana kayıttaki kulüp").isEqualTo("LOAN_IN");
        assertThat(byEa.get(fromSquadStatus).at("/fromClub").asText()).isEqualTo(otherName);
        assertThat(byEa.get(bought).at("/type").asText()).isEqualTo("TRANSFER_IN");

        assertThat(jdbc.queryForObject("SELECT budget_eur FROM career WHERE id = ?", Long.class, careerId)).isEqualTo(12_345_678L);
    }
}
