package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.base.BaseCatalogImporter.BaseImportResult;
import com.fcareer.ingestion.base.FcDumpLogParser;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class BaseCatalogImporterIntegrationTest {

    private static final long EXISTING_MOCK_ID = 900_000L;
    private static final long NEW_STRIKER = 999_000L;
    private static final long NEW_KEEPER = 999_001L;
    private static final long NO_TEAM = 999_002L;

    @Autowired
    private BaseCatalogImporter importer;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private Map<String, String> player(long id, int position, String name, String extra) {
        Map<String, String> row = new HashMap<>();
        row.put("playerid", String.valueOf(id));
        row.put("name", name);
        row.put("overallrating", "70");
        row.put("potential", "75");
        row.put("birthdate", "146942");
        row.put("height", "187");
        row.put("weight", "80");
        row.put("preferredfoot", "2");
        row.put("nationality", "55");
        row.put("gender", "0");
        row.put("preferredposition1", String.valueOf(position));
        row.put("preferredposition2", "-1");
        row.put("weakfootabilitytypecode", "4");
        row.put("skillmoves", "3");
        row.put("trait1", "0");
        row.put("trait2", "0");
        row.put("icontrait1", "0");
        row.put("icontrait2", "0");
        row.put("runningcode1", "2");
        row.put("runningcode2", "0");
        row.put("agility", "70");
        row.put("strength", "80");
        row.put("acceleration", "75");
        if (extra != null) {
            String[] kv = extra.split("=");
            row.put(kv[0], kv[1]);
        }
        return row;
    }

    private Map<String, List<Map<String, String>>> tables() {
        Map<String, String> striker = player(NEW_STRIKER, 25, "Test, Striker \"Jr\"", "trait1=3");
        striker.put("icontrait1", "1");
        Map<String, String> keeper = player(NEW_KEEPER, 0, "Test Keeper", "trait2=1");
        Map<String, String> homeless = player(NO_TEAM, 14, "Nobody", null);
        Map<String, String> existing = player(EXISTING_MOCK_ID, 25, "Already Here", null);
        return Map.of(
                "players", List.of(striker, keeper, homeless, existing),
                "teams", List.of(Map.of("teamid", "5000", "teamname", "Test United", "gender", "0"),
                        Map.of("teamid", "111592", "teamname", "Serbest", "gender", "0")),
                "teamplayerlinks", List.of(Map.of("playerid", String.valueOf(NEW_STRIKER), "teamid", "5000"),
                        Map.of("playerid", String.valueOf(NEW_KEEPER), "teamid", "111592"),
                        Map.of("playerid", String.valueOf(EXISTING_MOCK_ID), "teamid", "5000")),
                "leagueteamlinks", List.of(Map.of("teamid", "5000", "leagueid", "77")),
                "leagues", List.of(Map.of("leagueid", "77", "leaguename", "Test League", "isinternationalleague", "0")));
    }

    @Test
    void createsMissingPlayersWithMappedFieldsAndSkipsExistingAndUnlinked() {
        BaseImportResult result = importer.importMissing(tables(), (short) 27, false, true);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.existing()).isEqualTo(1);
        assertThat(result.skippedNoTeam()).isEqualTo(1);

        Map<String, Object> striker = jdbc.queryForMap("""
                SELECT b.full_name, b.dob, b.height_cm, b.preferred_foot, r.positions, r.skill_moves, r.weak_foot, r.accelerate,
                       r.playstyles::text AS playstyles, r.run_style1, c.name AS club, l.name AS league
                FROM base_player b JOIN player_rating r ON r.base_player_id = b.id
                JOIN club c ON c.id = r.club_id LEFT JOIN league l ON l.id = c.league_id
                WHERE b.ea_resource_id = ?""", NEW_STRIKER);
        assertThat(striker.get("full_name")).isEqualTo("Test, Striker \"Jr\"");
        assertThat(striker.get("dob").toString()).isEqualTo("1985-02-05");
        assertThat(((Number) striker.get("height_cm")).intValue()).isEqualTo(187);
        assertThat(striker.get("preferred_foot")).isEqualTo("Left");
        assertThat(((Number) striker.get("skill_moves")).intValue()).isEqualTo(4);
        assertThat(((Number) striker.get("weak_foot")).intValue()).isEqualTo(4);
        assertThat(striker.get("playstyles").toString()).contains("\"FinesseShot\": 2").contains("\"ChipShot\": 1");
        assertThat(((Number) striker.get("run_style1")).intValue()).isEqualTo(2);
        assertThat(striker.get("club")).isEqualTo("Test United");
        assertThat(striker.get("league")).isEqualTo("Test League");

        Map<String, Object> keeper = jdbc.queryForMap("""
                SELECT r.positions, r.playstyles::text AS playstyles, c.name AS club
                FROM base_player b JOIN player_rating r ON r.base_player_id = b.id JOIN club c ON c.id = r.club_id
                WHERE b.ea_resource_id = ?""", NEW_KEEPER);
        assertThat(keeper.get("playstyles").toString()).contains("FarThrow");
        assertThat(keeper.get("club")).isEqualTo("Free Agents");
        assertThat(basePlayerRepository.findByEaResourceId(NO_TEAM)).isEmpty();
    }

    @Test
    void overwriteReplacesRatingsOfExistingPlayersButKeepsTheirName() {
        Map<String, List<Map<String, String>>> tables = tables();
        tables.get("players").get(3).put("overallrating", "99");
        tables.get("players").get(3).put("preferredposition1", "14");

        BaseImportResult result = importer.importMissing(tables, (short) 27, true, false);

        assertThat(result.updated()).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT b.full_name, r.overall, array_to_string(r.positions, ',') AS positions
                FROM base_player b JOIN player_rating r ON r.base_player_id = b.id WHERE b.ea_resource_id = ?""", EXISTING_MOCK_ID);
        assertThat(((Number) row.get("overall")).intValue()).isEqualTo(99);
        assertThat(row.get("positions")).isEqualTo("CM");
        assertThat(row.get("full_name")).isNotEqualTo("Already Here");
    }

    @Test
    void parsesFcDumpLogLinesAndKeepsTheLastRunPerTable() {
        String log = String.join("\n",
                "17:00:00.1\tINFO\t[LUA API] FCDUMP\tBEGIN\tplayers\tfields=2\trecords=1\tvia=log",
                "17:00:00.2\tINFO\t[LUA API] FCDUMP\tHEADER\tplayers\tplayerid,name",
                "17:00:00.3\tINFO\t[LUA API] FCDUMP\tROW\tplayers\t1,Old",
                "17:00:00.4\tINFO\t[LUA API] FCDUMP\tEND\tplayers\t1",
                "unrelated line",
                "18:00:00.1\tINFO\t[LUA API] FCDUMP\tBEGIN\tplayers\tfields=2\trecords=2\tvia=log",
                "18:00:00.2\tINFO\t[LUA API] FCDUMP\tHEADER\tplayers\tplayerid,name",
                "18:00:00.3\tINFO\t[LUA API] FCDUMP\tROW\tplayers\t7,\"Lopez, Jr\"",
                "18:00:00.4\tINFO\t[LUA API] FCDUMP\tROW\tplayers\t8,Plain",
                "18:00:00.5\tINFO\t[LUA API] FCDUMP\tEND\tplayers\t2");

        Map<String, List<Map<String, String>>> tables = FcDumpLogParser.parse(new ByteArrayInputStream(log.getBytes(StandardCharsets.UTF_8)));

        assertThat(tables.get("players")).hasSize(2);
        assertThat(tables.get("players").get(0)).containsEntry("playerid", "7").containsEntry("name", "Lopez, Jr");
    }
}
