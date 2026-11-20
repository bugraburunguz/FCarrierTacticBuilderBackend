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
    private static final long BESIKTAS_TOP_SCORER = 207421L;
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

    @Test
    void planAgainstGalatasarayGivesOurElevenRestCandidatesNotesAndSetPieceMatchups() throws Exception {
        importExport(fixture());

        assertThat(data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan")).at("/restCandidates").size())
                .as("son 10 günde 2 maç: yoğun fikstür yok").isZero();
        long scorer = baseId(BESIKTAS_TOP_SCORER);
        for (String date : new String[] {"2027-02-23", "2027-02-25"}) {
            jdbc.update("INSERT INTO career_player_match_rating (career_id, player_id, match_date, minutes, position_code, rating) VALUES (?, ?, ?::date, 90, 25, 7)",
                    careerId, scorer, date);
        }

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/ourFormation/source").asText()).as("ana taktik yok: takımın varsayılan dizilişi").isEqualTo("TEAM_DEFAULT");
        assertThat(plan.at("/ourFormation/id").asText()).isEqualTo("4-1-4-1");
        JsonNode eleven = plan.at("/ourEleven");
        assertThat(eleven.size()).isEqualTo(11);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ?", Long.class, careerId);
        JsonNode rest = plan.at("/restCandidates/0");
        assertThat(rest.at("/playerId").asLong()).as("10 günde 4 maç").isEqualTo(scorer);
        assertThat(rest.at("/matches").asInt()).as("21, 23, 25, 27 Şubat").isEqualTo(4);
        assertThat(rest.at("/minutes").asInt()).isGreaterThanOrEqualTo(240);
        eleven.forEach((s) -> {
            assertThat(squad).contains(s.at("/playerId").asLong());
            assertThat(s.at("/lastRatings").isArray()).isTrue();
            assertThat(s.at("/lastRatings").size()).isLessThanOrEqualTo(5);
            assertThat(s.at("/restSuggested").asBoolean()).isEqualTo(s.at("/playerId").asLong() == scorer);
        });
        assertThat(plan.findValues("code").stream().map(JsonNode::asText)).as("yoğun oynayan ilk 11 oyuncusu için rotasyon notu").contains("ROTATE");
        assertThat(eleven.findValues("recentAverage").stream().filter((v) -> !v.isNull()).count()).as("son 5 not ortalaması").isPositive();

        JsonNode notes = plan.at("/tacticalNotes");
        assertThat(notes.size()).isBetween(1, 5);
        notes.forEach((n) -> {
            assertThat(n.at("/code").asText()).isNotBlank();
            assertThat(n.at("/text").asText()).as("metin messages.properties'ten, kod değil").isNotBlank().doesNotStartWith("match.note");
        });

        assertThat(plan.at("/setPieces/ourAerialThreats").size()).isPositive();
        assertThat(plan.at("/setPieces/theirAerialThreats").size()).isPositive();
        assertThat(plan.at("/setPieces/theirCornerTaker/name").asText()).isNotBlank();
        assertThat(plan.at("/missing").size()).as(plan.at("/missing").toString()).isZero();
    }

    @Test
    void planUsesTheMainTacticFormationWhenThereIsOne() throws Exception {
        importExport(fixture());
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())));

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/ourFormation/source").asText()).isEqualTo("MAIN_TACTIC");
        assertThat(plan.at("/ourFormation/id").asText()).isEqualTo("4-3-3");
        assertThat(plan.at("/ourEleven").size()).isEqualTo(11);
    }

    @Test
    void planFollowsTheUsersOwnLineupFromTheMainTactic() throws Exception {
        importExport(fixture());
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())));
        JsonNode engine = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));
        assertThat(engine.at("/lineupSource").asText()).isEqualTo("ENGINE");
        java.util.Map<String, Long> lineup = new java.util.LinkedHashMap<>();
        engine.at("/ourEleven").forEach((s) -> lineup.put(s.at("/slotId").asText(), s.at("/playerId").asLong()));
        List<String> wide = lineup.keySet().stream().filter((slot) -> slot.startsWith("L") || slot.startsWith("R")).toList();
        assertThat(wide).as(lineup.keySet().toString()).hasSizeGreaterThanOrEqualTo(2);
        Long first = lineup.get(wide.get(0));
        lineup.put(wide.get(0), lineup.get(wide.get(1)));
        lineup.put(wide.get(1), first);
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())), lineup);

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/lineupSource").asText()).isEqualTo("USER_LINEUP");
        java.util.Map<String, Long> placed = new java.util.HashMap<>();
        plan.at("/ourEleven").forEach((s) -> placed.put(s.at("/slotId").asText(), s.at("/playerId").asLong()));
        assertThat(placed).isEqualTo(lineup);

        java.util.Map<String, Long> stale = new java.util.LinkedHashMap<>(lineup);
        long bench = java.util.stream.StreamSupport.stream(data(get("/api/v1/careers/" + careerId + "/squad")).spliterator(), false)
                .map((s) -> s.at("/player/id").asLong()).filter((id) -> !lineup.containsValue(id)).findFirst().orElseThrow();
        stale.put("LWB", bench);
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())), stale);
        assertThat(data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan")).at("/lineupSource").asText())
                .as("dizilişte olmayan eski slot anahtarı kendi 11'i bozmaz").isEqualTo("USER_LINEUP");
    }

    @Test
    void theSamePlayerInTwoSlotsLeavesTheElevenToTheEngine() throws Exception {
        importExport(fixture());
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())));
        JsonNode engine = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));
        java.util.Map<String, Long> lineup = new java.util.LinkedHashMap<>();
        engine.at("/ourEleven").forEach((s) -> lineup.put(s.at("/slotId").asText(), s.at("/playerId").asLong()));
        String first = lineup.keySet().iterator().next();
        String last = lineup.keySet().stream().reduce((a, b) -> b).orElseThrow();
        lineup.put(last, lineup.get(first));
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())), lineup);

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/lineupSource").asText()).isEqualTo("ENGINE");
    }

    @Test
    void anIncompleteLineupLeavesTheElevenToTheEngine() throws Exception {
        importExport(fixture());
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())));
        JsonNode engine = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));
        JsonNode keeper = engine.at("/ourEleven/0");
        saveMainTactic("4-3-3", List.of(java.util.Map.of("slotId", "ST", "roleId", "poacher_attack", "tags", List.of())),
                java.util.Map.of(keeper.at("/slotId").asText(), keeper.at("/playerId").asLong()));

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/lineupSource").asText()).isEqualTo("ENGINE");
        assertThat(plan.at("/ourEleven").size()).isEqualTo(11);
    }

    @Test
    void anAttackingLeftWingerAgainstFenerbahcesStrongRightSideIsFlaggedAsAnExposedFlank() throws Exception {
        importExport(fixture());
        JsonNode balanced = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + FENERBAHCE + "/plan"));
        assertThat(balanced.at("/flanks/left/verdict").asText()).as("varsayılan Winger (Balanced): sol kanat dengeli").isEqualTo("BALANCED");
        saveMainTactic("4-1-4-1", List.of(java.util.Map.of("slotId", "LM", "roleId", "winger_attack", "tags", List.of())));

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + FENERBAHCE + "/plan"));

        JsonNode left = plan.at("/flanks/left");
        assertThat(left.at("/verdict").asText()).isEqualTo("EXPOSED");
        assertThat(left.at("/ours/attackLean").asInt()).isGreaterThan(balanced.at("/flanks/left/ours/attackLean").asInt());
        List<String> codes = new ArrayList<>();
        plan.at("/tacticalNotes").forEach((n) -> codes.add(n.at("/code").asText()));
        assertThat(codes).as(codes.toString()).startsWith("KEY_THREAT", "FLANK_EXPOSED");
        assertThat(plan.at("/tacticalNotes/1/text").asText()).startsWith("Sol kanadımız açık: rakibin sağ kanadında")
                .contains(left.at("/theirs/keyThreat/name").asText(), "Winger (Attack) rolüyle");
    }

    @Test
    void planCarriesBothFlankMatchupsAndFlankNotesComeRightAfterTheKeyThreat() throws Exception {
        importExport(fixture());

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        for (String side : List.of("left", "right")) {
            JsonNode flank = plan.at("/flanks/" + side);
            assertThat(flank.at("/side").asText()).isEqualTo(side.toUpperCase(java.util.Locale.ROOT));
            assertThat(flank.at("/verdict").asText()).isIn("EXPOSED", "OPPORTUNITY", "BALANCED");
            assertThat(flank.at("/ours/players").size()).isPositive();
            assertThat(flank.at("/theirs/players").size()).isPositive();
            assertThat(flank.at("/theirs/keyThreat/name").asText()).isNotBlank();
            assertThat(flank.at("/ours/players/0/role").asText()).isNotBlank();
            assertThat(flank.at("/ours/defensiveCover").asInt()).isBetween(0, 100);
            assertThat(flank.at("/theirs/threat").asInt()).isBetween(0, 100);
        }
        List<String> codes = new ArrayList<>();
        plan.at("/tacticalNotes").forEach((n) -> codes.add(n.at("/code").asText()));
        int firstOther = java.util.stream.IntStream.range(0, codes.size()).filter((i) -> !codes.get(i).startsWith("FLANK_") && !codes.get(i).equals("KEY_THREAT"))
                .findFirst().orElse(codes.size());
        assertThat(codes.subList(firstOther, codes.size())).as(codes.toString()).noneMatch((c) -> c.startsWith("FLANK_"));
        assertThat(codes.indexOf("KEY_THREAT")).as(codes.toString()).isLessThanOrEqualTo(0);
        assertThat(codes.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void aStaleMainTacticFallsBackToTheTeamDefaultInsteadOfFailing() throws Exception {
        importExport(fixture());
        saveMainTactic("4-3-3", List.of());
        jdbc.update("UPDATE user_tactic SET formation = '9-9-9' WHERE career_id = ?", careerId);

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        assertThat(plan.at("/ourFormation/source").asText()).isEqualTo("TEAM_DEFAULT");
        assertThat(plan.at("/ourFormation/id").asText()).isEqualTo("4-1-4-1");
        assertThat(plan.at("/ourEleven").size()).isEqualTo(11);
    }

    private void saveMainTactic(String formation, List<java.util.Map<String, Object>> slots) throws Exception {
        saveMainTactic(formation, slots, java.util.Map.of());
    }

    private void saveMainTactic(String formation, List<java.util.Map<String, Object>> slots, java.util.Map<String, Long> lineup) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>(java.util.Map.of("careerId", careerId, "tactic", java.util.Map.of("formation", formation, "slots", slots)));
        if (!lineup.isEmpty()) {
            body.put("lineup", lineup);
        }
        JsonNode saved = support.json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/user-tactics/1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)), token);
        assertThat(saved.at("/success").asBoolean()).as(saved.toString()).isTrue();
        support.json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/user-tactics/1/main?careerId=" + careerId), token);
    }

    @Test
    void planOnAnOldExportListsWhatIsMissing() throws Exception {
        importExport(withoutTables(fixture(), "formations", "career_playermatchratinghistory"));

        JsonNode plan = data(get("/api/v1/careers/" + careerId + "/match/opponents/" + GALATASARAY + "/plan"));

        List<String> missing = new ArrayList<>();
        plan.at("/missing").forEach((m) -> missing.add(m.asText()));
        assertThat(missing).contains("OUR_FORMATION", "MATCH_RATINGS", "FLANKS");
        assertThat(plan.has("flanks")).isFalse();
        assertThat(plan.at("/ourEleven").size()).isZero();
        assertThat(plan.at("/restCandidates").size()).isZero();
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
