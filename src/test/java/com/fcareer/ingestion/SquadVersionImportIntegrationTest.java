package com.fcareer.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersion;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class SquadVersionImportIntegrationTest {

    private static final short GAME = 27;
    private static final long EXISTING_PLAYER = 900_000L;
    private static final long NEW_PLAYER = 199_500L;
    private static final String PREFIX = "10:00:00.1\tINFO\t[LUA API] FCX2\t";
    private static final String BUILD_LINE = "11:49:31.399109\tINFO\tGame Version:            1.0.142.20665";
    private static final String PLAYER_HEADER = "[\"playerid\",\"overallrating\",\"potential\",\"preferredposition1\",\"preferredposition2\","
            + "\"acceleration\",\"agility\",\"strength\",\"weakfootabilitytypecode\",\"skillmoves\",\"gender\",\"birthdate\",\"height\",\"weight\",\"preferredfoot\"]";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        jdbc.update("UPDATE club SET ea_team_id = NULL WHERE ea_team_id < 1000000");
    }

    @Test
    void eachOverwriteUploadArchivesThePreviousVersionAndOpensANewOne() throws Exception {
        SquadVersion before = latest();
        int original = liveOverall();
        int first = original == 81 ? 82 : 81;
        int second = first + 3;

        JsonNode v1 = upload(log(true, "2026-11-20 09:00:00", List.of(playerRow(EXISTING_PLAYER, first))), true, null);
        JsonNode v2 = upload(log(true, "2026-11-22 11:49:31", List.of(playerRow(EXISTING_PLAYER, second))), true, null);

        assertThat(v1.path("squadVersionNo").asInt()).isEqualTo(before.versionNo() + 1);
        assertThat(v1.path("archivedPlayers").asInt()).isPositive();
        assertThat(v2.path("squadVersionNo").asInt()).isEqualTo(before.versionNo() + 2);
        assertThat(archivedOverall(before.id())).isEqualTo(original);
        assertThat(archivedOverall(v1.path("squadVersionId").asLong())).isEqualTo(first);
        assertThat(liveOverall()).isEqualTo(second);

        SquadVersion opened = squadVersions.findById(v2.path("squadVersionId").asLong()).orElseThrow();
        assertThat(opened.label()).isEqualTo("Kadro güncellemesi 22.11.2026 (1.0.142)");
        assertThat(opened.gameBuild()).isEqualTo("1.0.142.20665");
        assertThat(opened.exportedAt()).isEqualTo(Instant.parse("2026-11-22T08:49:31Z"));
        assertThat(opened.playerCount()).isEqualTo(catalogCount());
        assertThat(squadVersions.findLatest(GAME).orElseThrow().id()).isEqualTo(opened.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dataset_version WHERE squad_version_id = ?", Integer.class, opened.id())).isEqualTo(1);
    }

    @Test
    void anAdminLabelWinsAndAMissingBuildLeavesNoParentheses() throws Exception {
        int next = liveOverall() == 70 ? 71 : 70;

        JsonNode labelled = upload(log(false, "2026-11-22 11:49:31", List.of(playerRow(EXISTING_PLAYER, next))), true, "Elle etiket");
        JsonNode auto = upload(log(false, "2026-11-23 01:30:00", List.of(playerRow(EXISTING_PLAYER, next + 1))), true, null);

        assertThat(squadVersions.findById(labelled.path("squadVersionId").asLong()).orElseThrow().label()).isEqualTo("Elle etiket");
        SquadVersion autoVersion = squadVersions.findById(auto.path("squadVersionId").asLong()).orElseThrow();
        assertThat(autoVersion.label()).isEqualTo("Kadro güncellemesi 23.11.2026");
        assertThat(autoVersion.gameBuild()).isNull();
    }

    @Test
    void anOverwriteUploadWithoutChangesOpensNoVersionAndArchivesNothing() throws Exception {
        int next = liveOverall() == 75 ? 76 : 75;
        upload(log(true, "2026-11-22 11:49:31", List.of(playerRow(EXISTING_PLAYER, next))), true, null);
        SquadVersion before = latest();
        int archived = archiveRows();

        JsonNode same = upload(log(true, "2026-11-22 11:49:31", List.of(playerRow(EXISTING_PLAYER, next))), true, null);

        assertThat(same.path("squadVersionId").isNull() || same.path("squadVersionId").isMissingNode()).isTrue();
        assertThat(same.path("archivedPlayers").asInt()).isZero();
        assertThat(latest().id()).isEqualTo(before.id());
        assertThat(archiveRows()).isEqualTo(archived);
    }

    @Test
    void aNonOverwriteUploadAddsPlayersWithoutOpeningAVersion() throws Exception {
        SquadVersion before = latest();
        int archived = archiveRows();
        int next = liveOverall() == 60 ? 61 : 60;

        JsonNode result = upload(log(true, "2026-11-22 11:49:31", List.of(playerRow(EXISTING_PLAYER, next), playerRow(NEW_PLAYER, 66))), false, null);

        assertThat(result.path("created").asInt()).isEqualTo(1);
        assertThat(result.path("squadVersionId").isNull() || result.path("squadVersionId").isMissingNode()).isTrue();
        assertThat(latest().id()).isEqualTo(before.id());
        assertThat(archiveRows()).isEqualTo(archived);
        assertThat(liveOverall()).isNotEqualTo(next);
    }

    private SquadVersion latest() {
        return squadVersions.findLatest(GAME).orElseThrow();
    }

    private int liveOverall() {
        return jdbc.queryForObject("SELECT r.overall FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE b.ea_resource_id = ? AND r.game_version_id = ?",
                Integer.class, EXISTING_PLAYER, GAME);
    }

    private int archivedOverall(long squadVersionId) {
        return jdbc.queryForObject("SELECT a.overall FROM player_rating_archive a JOIN base_player b ON b.id = a.base_player_id WHERE b.ea_resource_id = ? AND a.squad_version_id = ?",
                Integer.class, EXISTING_PLAYER, squadVersionId);
    }

    private int archiveRows() {
        return jdbc.queryForObject("SELECT count(*) FROM player_rating_archive", Integer.class);
    }

    private int catalogCount() {
        return jdbc.queryForObject("SELECT count(*) FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.game_version_id = ? AND b.owner_career_id IS NULL",
                Integer.class, GAME);
    }

    private JsonNode upload(String log, boolean overwrite, String label) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/admin/ingestion/import-base");
        request.file(new MockMultipartFile("log", "base.log", "text/plain", log.getBytes(StandardCharsets.UTF_8)));
        request.param("overwrite", String.valueOf(overwrite));
        if (label != null) {
            request.param("label", label);
        }
        String body = mvc.perform(request.header("X-Admin-Key", "test-key")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private static String playerRow(long id, int overall) {
        return "[" + id + "," + overall + ",80,25,-1,75,70,80,4,3,0,146942,187,80,2]";
    }

    private static String log(boolean withBuild, String realTime, List<String> players) {
        List<String> lines = new ArrayList<>();
        if (withBuild) {
            lines.add(BUILD_LINE);
        }
        lines.add(PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.5\",\"context\":{\"inCareer\":false,\"mode\":\"BASE\",\"realTime\":\""
                + realTime + "\"}}");
        table(lines, "players", PLAYER_HEADER, players);
        table(lines, "teams", "[\"teamid\",\"teamname\",\"gender\"]", List.of("[5000,\"Test United\",0]"));
        List<String> links = new ArrayList<>();
        for (String row : players) {
            links.add("[" + row.substring(1, row.indexOf(',')) + ",5000]");
        }
        table(lines, "teamplayerlinks", "[\"playerid\",\"teamid\"]", links);
        lines.add(PREFIX + "DONE\t[]");
        return String.join("\n", lines);
    }

    private static void table(List<String> lines, String name, String header, List<String> rows) {
        lines.add(PREFIX + "HEADER\t" + name + "\t" + header);
        lines.add(PREFIX + "CHUNK\t" + name + "\t1\t[" + String.join(",", rows) + "]");
        lines.add(PREFIX + "END\t" + name + "\t" + rows.size() + "\t" + fnv(rows) + "\t1");
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
}
