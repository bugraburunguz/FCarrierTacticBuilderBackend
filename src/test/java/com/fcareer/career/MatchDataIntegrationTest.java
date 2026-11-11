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
class MatchDataIntegrationTest {

    private static final int BESIKTAS = 327;
    private static final int OPPONENT = 325;
    private static final long TOP_SCORER_EA = 243245L;
    private static final int RATINGS_IN_EXP12 = 75;
    private static final int RATINGS_ACROSS_EXPORTS = 170;
    private static final int SEASON_ROWS_IN_EXP6 = 22;
    private static final int SEASON_ROWS_IN_EXP12 = 30;

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
    void matchRatingsAccumulateAcrossExportsAndReimportDoesNotDuplicate() throws Exception {
        importExport(fixture(6));
        importExport(fixture(9));
        importExport(fixture(12));

        assertThat(ratingCount()).as("oyuncu başına 15 maçlık pencere düşse de notlar birikir").isEqualTo(RATINGS_ACROSS_EXPORTS).isGreaterThan(RATINGS_IN_EXP12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT player_id, match_date FROM career_player_match_rating WHERE career_id = ? GROUP BY 1, 2) t",
                Integer.class, careerId)).as("(oyuncu, tarih) tekil").isEqualTo(RATINGS_ACROSS_EXPORTS);

        importExport(fixture(12));
        assertThat(ratingCount()).as("aynı export iki kez: çoğalma yok").isEqualTo(RATINGS_ACROSS_EXPORTS);
    }

    @Test
    void seasonStatsAreTheLatestSnapshotAndAnExportWithoutTheTablesKeepsEverything() throws Exception {
        importExport(fixture(12));
        assertThat(seasonCount()).isEqualTo(SEASON_ROWS_IN_EXP12);
        assertThat(jdbc.queryForList("SELECT DISTINCT team_id FROM career_player_season_stat WHERE career_id = ?", Integer.class, careerId))
                .as("rakip takım da saklanır").contains(BESIKTAS, OPPONENT);
        importExport(fixture(6));
        assertThat(seasonCount()).as("son içe aktarmanın görüntüsü, birikmez").isEqualTo(SEASON_ROWS_IN_EXP6);
        int ratings = ratingCount();

        importExport(withoutMatchTables(fixture(12)));

        assertThat(ratingCount()).as("eski betik: birikmiş notlar silinmez").isEqualTo(ratings);
        assertThat(seasonCount()).as("eski betik: sezon görüntüsü korunur").isEqualTo(SEASON_ROWS_IN_EXP6);
    }

    @Test
    void readEndpointsServeThePlayerTheSquadAndTheLeaders() throws Exception {
        importExport(fixture(6));
        importExport(fixture(12));
        long scorer = jdbc.queryForObject("SELECT id FROM base_player WHERE owner_career_id = ? AND career_source_id = ?", Long.class, careerId, TOP_SCORER_EA);

        JsonNode player = data(get("/api/v1/careers/" + careerId + "/players/" + scorer + "/matches"));
        assertThat(player.at("/ratings").size()).isGreaterThan(0);
        JsonNode first = player.at("/ratings/0");
        assertThat(first.has("date") && first.has("minutes") && first.has("rating") && first.has("position")).as(first.toString()).isTrue();
        List<String> dates = new ArrayList<>();
        player.at("/ratings").forEach((r) -> dates.add(r.get("date").asText()));
        assertThat(dates).as("en yeni maç önce").isSortedAccordingTo((a, b) -> b.compareTo(a));
        JsonNode season = player.at("/seasons/0");
        assertThat(season.at("/teamId").asInt()).isEqualTo(BESIKTAS);
        assertThat(season.has("competitionId") && season.has("appearances") && season.has("goals") && season.has("assists") && season.has("averageRating")).isTrue();

        JsonNode form = data(get("/api/v1/careers/" + careerId + "/squad/form"));
        JsonNode row = null;
        for (JsonNode r : form) {
            if (r.at("/playerId").asLong() == scorer) {
                row = r;
            }
        }
        assertThat(row).as("kadro özeti golcüyü içerir").isNotNull();
        assertThat(row.at("/lastRatings").size()).isBetween(1, 5);
        assertThat(row.at("/goals").asInt()).isPositive();
        assertThat(row.at("/averageRating").asDouble()).isBetween(1.0, 10.0);

        JsonNode competitions = data(get("/api/v1/careers/" + careerId + "/stats/competitions"));
        assertThat(competitions.size()).isPositive();
        int competition = competitions.at("/0/competitionId").asInt();
        JsonNode leaders = data(get("/api/v1/careers/" + careerId + "/stats/leaders?competition=" + competition + "&metric=goals"));
        List<Integer> goals = new ArrayList<>();
        leaders.forEach((l) -> goals.add(l.at("/goals").asInt()));
        assertThat(goals).isNotEmpty().isSortedAccordingTo((a, b) -> b - a);
        assertThat(leaders.at("/0/playerName").asText()).isNotBlank();
        assertThat(leaders.at("/0/teamName").asText()).isNotBlank();

        List<Integer> recent = new ArrayList<>();
        row.at("/lastRatings").forEach((r) -> recent.add(r.asInt()));
        List<Integer> newest = new ArrayList<>();
        for (int i = 0; i < Math.min(5, player.at("/ratings").size()); i++) {
            newest.add(player.at("/ratings/" + i + "/rating").asInt());
        }
        assertThat(recent).as("son 5 not, en yeni önce").isEqualTo(newest);

        JsonNode byRating = data(get("/api/v1/careers/" + careerId + "/stats/leaders?metric=RATING"));
        List<Double> averages = new ArrayList<>();
        byRating.forEach((l) -> {
            averages.add(l.at("/averageRating").asDouble());
            assertThat(l.at("/appearances").asInt()).as("not krallığında en az 3 maç").isGreaterThanOrEqualTo(3);
        });
        assertThat(averages).isNotEmpty().isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(support.json(get("/api/v1/careers/" + careerId + "/stats/leaders?metric=xg"), token).at("/success").asBoolean()).isFalse();
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

    private int ratingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM career_player_match_rating WHERE career_id = ?", Integer.class, careerId);
    }

    private int seasonCount() {
        return jdbc.queryForObject("SELECT count(*) FROM career_player_season_stat WHERE career_id = ?", Integer.class, careerId);
    }

    private static String fixture(int seq) throws IOException {
        return new String(new ClassPathResource("le-export/fca22-exp" + seq + ".log").getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    private static String withoutMatchTables(String log) {
        Set<String> dropped = new HashSet<>(Set.of("\tcareer_playermatchratinghistory\t", "\tplayer_season_stats\t"));
        return Arrays.stream(log.split("\n")).filter((l) -> dropped.stream().noneMatch(l::contains)).collect(Collectors.joining("\n"));
    }
}
