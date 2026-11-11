package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@IntegrationTest
class MatchCenterIntegrationTest {

    private static final int BESIKTAS = 327;
    private static final int GALATASARAY = 325;
    private static final int FENERBAHCE = 326;
    private static final int EUROPEAN_RIVAL = 1943;
    private static final long GALATASARAY_PENALTY_TAKER = 241721L;
    private static final long GALATASARAY_CAPTAIN = 229905L;

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

    private CareerTestSupport support;
    private String token;
    private long careerId;

    @BeforeEach
    void setUp() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        token = support.register();
        careerId = support.premiumCareer(token, null);
    }

    @Test
    void opponentsAreLeagueRivalsAndTeamsFromSharedCompetitions() throws Exception {
        importExport(fixture());

        JsonNode opponents = data(get("/api/v1/careers/" + careerId + "/match/opponents"));

        List<Integer> ids = new ArrayList<>();
        opponents.forEach((o) -> {
            ids.add(o.at("/teamId").asInt());
            assertThat(o.at("/name").asText()).isNotBlank();
            assertThat(o.at("/overall").asInt()).isPositive();
        });
        assertThat(ids).contains(GALATASARAY, FENERBAHCE, EUROPEAN_RIVAL).doesNotContain(BESIKTAS);
        JsonNode galatasaray = opponents.findParents("teamId").stream().filter((o) -> o.at("/teamId").asInt() == GALATASARAY).findFirst().orElseThrow();
        assertThat(galatasaray.at("/sameLeague").asBoolean()).isTrue();
        assertThat(galatasaray.at("/leagueName").asText()).isNotBlank();
        JsonNode european = opponents.findParents("teamId").stream().filter((o) -> o.at("/teamId").asInt() == EUROPEAN_RIVAL).findFirst().orElseThrow();
        assertThat(european.at("/sameLeague").asBoolean()).isFalse();
        assertThat(european.at("/competitionIds").size()).isPositive();
    }

    @Test
    void galatasarayReportCarriesFormationLinesLikelyElevenDangerSetPiecesAndCards() throws Exception {
        importExport(fixture());

        JsonNode report = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/report"));

        assertThat(report.at("/formation/name").asText()).isEqualTo("4-2-3-1");
        assertThat(report.at("/formation/defaultFormation").asBoolean()).as("varsayılan diziliş; AI maç içinde değiştirebilir").isTrue();
        assertThat(report.at("/formation/slots").size()).isEqualTo(11);
        assertThat(report.at("/formation/slots/0/position").asText()).isEqualTo("GK");

        assertThat(report.at("/lines/opponent/attack").asInt()).isPositive();
        assertThat(report.at("/lines/ours/attack").asInt()).isPositive();
        assertThat(report.has("buildUpPlay") && report.has("defensiveDepth")).isTrue();

        JsonNode eleven = report.at("/likelyEleven");
        assertThat(eleven.size()).isEqualTo(11);
        Set<Long> elevenIds = new HashSet<>();
        eleven.forEach((s) -> {
            elevenIds.add(s.at("/playerId").asLong());
            assertThat(s.at("/position").asText()).isNotBlank();
            assertThat(s.at("/overall").asInt()).isPositive();
            assertThat(s.has("form")).as("LE formu canlı değil (hep 3), yanıtta yok").isFalse();
            assertThat(s.has("averageRating")).as("sezon notu, az maçta null").isTrue();
        });
        assertThat(eleven.findValues("averageRating").stream().filter((v) -> !v.isNull()).count()).as("ilk 11'in çoğunun sezon notu var").isGreaterThan(5);
        assertThat(elevenIds).hasSize(11);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ? AND team_id = ? AND player_id IN ("
                + elevenIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")", Integer.class, careerId, GALATASARAY))
                .as("tahmini ilk 11 rakip kadrosundan").isEqualTo(11);

        assertThat(report.at("/dangerousPlayers").size()).isPositive();
        JsonNode danger = report.at("/dangerousPlayers/0");
        assertThat(danger.has("averageRating") && danger.has("goals") && danger.has("assists")).isTrue();
        assertThat(danger.has("form")).isFalse();

        assertThat(report.at("/setPieces/penaltyTaker/playerId").asLong()).isEqualTo(baseId(GALATASARAY_PENALTY_TAKER));
        assertThat(report.at("/setPieces/captain/playerId").asLong()).isEqualTo(baseId(GALATASARAY_CAPTAIN));
        assertThat(report.at("/setPieces/freeKickTaker/name").asText()).isNotBlank();
        assertThat(report.at("/cardRisk").isArray()).isTrue();
        assertThat(report.at("/missing").size()).as(report.at("/missing").toString()).isZero();
    }

    @Test
    void anOldExportStillAnswersWithTheMissingSectionsListed() throws Exception {
        importExport(withoutTables(fixture(), "formations", "player_season_stats"));

        JsonNode report = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/report"));

        List<String> missing = new ArrayList<>();
        report.at("/missing").forEach((m) -> missing.add(m.asText()));
        assertThat(missing).contains("FORMATION", "SEASON_STATS");
        assertThat(report.at("/formation").isMissingNode() || report.at("/formation").isNull()).isTrue();
        assertThat(report.at("/likelyEleven").size()).as("diziliş yokken de 4-4-2 varsayımıyla değil, boş").isZero();
    }

    @Test
    void anUnknownTeamAndOurOwnTeamAreNotFound() throws Exception {
        importExport(fixture());

        for (int teamId : new int[] {999_999, BESIKTAS}) {
            int status = mockMvc.perform(get("/api/v1/careers/" + careerId + "/match/opponents/" + teamId + "/report")
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
            assertThat(status).as("takım " + teamId).isEqualTo(404);
        }
    }

    private long baseId(long eaId) {
        return jdbc.queryForObject("SELECT id FROM base_player WHERE owner_career_id = ? AND career_source_id = ?", Long.class, careerId, eaId);
    }

    private void importExport(String log) throws Exception {
        JsonNode result = support.json(multipart("/api/v1/careers/" + careerId + "/import")
                .file(new MockMultipartFile("log", "career.log", "text/plain", log.getBytes(StandardCharsets.ISO_8859_1))), token);
        assertThat(result.at("/success").asBoolean()).as(result.toString()).isTrue();
    }

    private JsonNode data(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        JsonNode body = support.json(request, token);
        assertThat(body.at("/success").asBoolean()).as(body.toString()).isTrue();
        return body.at("/data");
    }

    private static String fixture() throws IOException {
        return new String(new ClassPathResource("le-export/fca23-exp12.log").getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    private static String withoutTables(String log, String... tables) {
        List<String> marks = Arrays.stream(tables).map((t) -> "\t" + t + "\t").toList();
        return Arrays.stream(log.split("\n")).filter((l) -> marks.stream().noneMatch(l::contains)).collect(Collectors.joining("\n"));
    }
}
