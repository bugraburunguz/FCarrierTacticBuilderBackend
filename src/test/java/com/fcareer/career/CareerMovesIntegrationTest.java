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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Kadro hareketleri (ana kayda göre): kariyer kaydındaki takım ile temel katalogdaki (oyunun ana kaydı) kulüp karşılaştırılır.
 * Ana kayıtta başka kulüpte olup kariyerde bizde olan → gelen transfer; kiralık kaydı varsa gelen kiralık. Ana kayıtta bizde olup kariyerde başka
 * takımda olan → giden transfer; kiralık kaynağı biz isek giden kiralık. Bedel verisi gerekmez.
 */
@IntegrationTest
class CareerMovesIntegrationTest {

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

    /** Kulübün temel katalogdaki ilk n oyuncusunun EA kimliği (rastgele değil: id sırası). */
    private List<Long> basePlayersOf(long clubId, int n) {
        return jdbc.queryForList("""
                SELECT b.ea_resource_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.club_id = ? AND r.game_version_id = 27 AND b.owner_career_id IS NULL AND b.ea_resource_id IS NOT NULL
                 ORDER BY b.id LIMIT ?""", Long.class, clubId, n);
    }

    private int eaTeamId(long clubId) {
        Integer current = jdbc.queryForObject("SELECT ea_team_id FROM club WHERE id = ?", Integer.class, clubId);
        if (current != null) {
            return current;
        }
        int assigned = 7_000_000 + (int) clubId;
        jdbc.update("UPDATE club SET ea_team_id = ? WHERE id = ?", assigned, clubId);
        return assigned;
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void movesAreDerivedFromTheBaseRecordAndLoanRows() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        var us = clubRepository.findAll().get(0);
        long otherClubId = jdbc.queryForObject("""
                SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id AND b.owner_career_id IS NULL
                 WHERE r.game_version_id = 27 AND r.club_id <> ? GROUP BY r.club_id HAVING count(*) >= 3
                 ORDER BY r.club_id LIMIT 1""", Long.class, us.getId());
        String otherName = jdbc.queryForObject("SELECT name FROM club WHERE id = ?", String.class, otherClubId);
        List<Long> ours = basePlayersOf(us.getId(), 3);
        List<Long> theirs = basePlayersOf(otherClubId, 3);
        long stays = ours.get(0);
        long soldOut = ours.get(1);
        long loanedOut = ours.get(2);
        long bought = theirs.get(0);
        long loanedIn = theirs.get(1);
        long untouched = theirs.get(2);

        // Gerçek katalogda kulüplerin EA takım kimliği dolu (ör. Port Vale 1928); test kataloğunda boşsa benzersiz bir değer verilir.
        int usTeam = eaTeamId(us.getId());
        int otherTeam = eaTeamId(otherClubId);
        String players = String.join("\n", List.of(stays, soldOut, loanedOut, bought, loanedIn, untouched).stream().map(id -> id + ",70,75").toList());
        String links = String.join("\n", List.of(stays + "," + usTeam, soldOut + "," + otherTeam, loanedOut + "," + otherTeam, bought + "," + usTeam,
                loanedIn + "," + usTeam, untouched + "," + otherTeam));
        var request = multipart("/api/v1/careers/" + careerId + "/import")
                .file(csv("players", "playerid,overallrating,potential\n" + players))
                .file(csv("teams", "teamid,teamname,overallrating,gender\n" + usTeam + ",\"" + us.getName() + "\",80,0\n" + otherTeam + ",\"" + otherName
                        + "\",70,0\n111592,Serbest,60,0\n"))
                .file(csv("teamplayerlinks", "playerid,teamid\n" + links))
                .file(csv("playerloans", "playerid,teamidloanedfrom\n" + loanedIn + "," + otherTeam + "\n" + loanedOut + "," + usTeam));
        support.json(request, token);

        JsonNode moves = support.json(get("/api/v1/careers/" + careerId + "/moves").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON), null).at("/data");
        Map<String, JsonNode> byType = new HashMap<>();
        StreamSupport.stream(moves.spliterator(), false).forEach(m -> byType.put(m.at("/type").asText() + ":" + m.at("/eaId").asLong(), m));

        assertThat(byType).containsOnlyKeys("TRANSFER_IN:" + bought, "LOAN_IN:" + loanedIn, "TRANSFER_OUT:" + soldOut, "LOAN_OUT:" + loanedOut);
        assertThat(byType.get("TRANSFER_IN:" + bought).at("/fromClub").asText()).isEqualTo(otherName);
        assertThat(byType.get("LOAN_IN:" + loanedIn).at("/fromClub").asText()).isEqualTo(otherName);
        assertThat(byType.get("TRANSFER_OUT:" + soldOut).at("/toClub").asText()).isEqualTo(otherName);
        assertThat(byType.get("LOAN_OUT:" + loanedOut).at("/toClub").asText()).isEqualTo(otherName);
        assertThat(byType.get("TRANSFER_IN:" + bought).at("/name").asText()).isNotBlank();
        assertThat(byType.get("TRANSFER_IN:" + bought).at("/overall").asInt()).isEqualTo(70);

        // Oyuncu sayfası: kiralık giden "Kadronda" değil "kiralıkta"; kiralık gelen kiralık ama bizden gitmemiş.
        JsonNode out = contract(careerId, byType.get("LOAN_OUT:" + loanedOut).at("/playerId").asLong(), token);
        assertThat(out.at("/onLoan").asBoolean()).isTrue();
        assertThat(out.at("/loanedOut").asBoolean()).isTrue();
        JsonNode in = contract(careerId, byType.get("LOAN_IN:" + loanedIn).at("/playerId").asLong(), token);
        assertThat(in.at("/onLoan").asBoolean()).isTrue();
        assertThat(in.at("/loanedOut").asBoolean()).isFalse();
    }

    private JsonNode contract(long careerId, long playerId, String token) throws Exception {
        return support.json(get("/api/v1/players/" + playerId + "/contract?career=" + careerId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null).at("/data");
    }

    @Test
    void anotherUsersCareerIsNotFound() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        String stranger = support.register();
        var response = mockMvc.perform(get("/api/v1/careers/" + careerId + "/moves").header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
