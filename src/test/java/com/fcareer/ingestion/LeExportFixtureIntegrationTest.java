package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.base.BaseUploads;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LE-01 / LE-02: gerçek Live Editor dökümünün (Ekim 2026, Türkçe lig adları) küçültülmüş kopyasıyla. Lig/takım ayrımı ADLA değil ID ile yapılır:
 * 76 "Erkekler - Diğer" katalogdan dışlanır, 78/2136 milli takımlar kulüp değildir, 382 "Serbest" serbest oyuncudur, 2000/2028 yer tutucudur.
 */
@IntegrationTest
class LeExportFixtureIntegrationTest {

    private static final String DIR = "le-export/fc27-2026-10/";
    private static final short GAME_VERSION = 27;

    /** Kulüpler yalnız yeni oyuncular için çözülür; testler arasında bırakılan durum korunur, temizlik sınıf başına bir kez. */
    private static boolean prepared;

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
        // Paylaşılan test veritabanında başka sınıfların küçük (sahte) takım kimlikleri kulüplere yazılmış olabilir.
        if (!prepared) {
            jdbc.update("UPDATE club SET ea_team_id = NULL WHERE ea_team_id < 1000000");
            prepared = true;
        }
    }

    private static Map<String, List<Map<String, String>>> tables() throws IOException {
        Map<String, MultipartFile> files = new HashMap<>();
        for (String name : List.of("leagues", "leagueteamlinks", "players", "teamplayerlinks", "teams")) {
            byte[] bytes = new ClassPathResource(DIR + name + ".csv").getInputStream().readAllBytes();
            files.put(name, new MockMultipartFile(name, name + ".csv", "text/csv", bytes));
        }
        return BaseUploads.toTables(null, files);
    }

    private static Set<Integer> teamsInLeague(Map<String, List<Map<String, String>>> tables, int leagueId) {
        return tables.get("leagueteamlinks").stream().filter(r -> Integer.parseInt(r.get("leagueid")) == leagueId)
                .map(r -> Integer.parseInt(r.get("teamid"))).collect(Collectors.toSet());
    }

    private int clubsWithEaTeam(Set<Integer> teamIds) {
        return teamIds.stream().mapToInt(id -> jdbc.queryForObject("SELECT count(*) FROM club WHERE ea_team_id = ?", Integer.class, id)).sum();
    }

    @Test
    void leagueAndTeamKindsAreRecognisedByIdNotByTurkishName() throws Exception {
        Map<String, List<Map<String, String>>> tables = tables();
        Set<Integer> otherMen = teamsInLeague(tables, 76);
        Set<Integer> placeholders = teamsInLeague(tables, 2000);
        placeholders.addAll(teamsInLeague(tables, 2028));
        Set<Integer> national = teamsInLeague(tables, 78);
        national.addAll(teamsInLeague(tables, 2136));
        assertThat(otherMen).isNotEmpty();
        assertThat(national).isNotEmpty();

        BaseCatalogImporter.BaseImportResult result = importer.importMissing(tables, GAME_VERSION, false, true);

        assertThat(result.created()).isPositive();
        assertThat(clubsWithEaTeam(otherMen)).as("76 Erkekler - Diğer kulüpleri katalogdan dışlanır").isZero();
        assertThat(clubsWithEaTeam(placeholders)).as("2000/2028 yer tutucu").isZero();
        assertThat(clubsWithEaTeam(national)).as("78/2136 milli takım kulüp değildir").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM league WHERE name IN ('Erkekler - Diğer', 'Erkek Milli', 'Kadın Milli Kup.', 'DÜNYA LİGİ') OR name LIKE '*LeagueName%'", Integer.class)).isZero();
    }

    /**
     * MGR-a madde 1: katalog içe aktarması kulüp profilini (club_profile) doldurur — prestij (1–10), popülerlik, kulüp değeri (export bin € → €),
     * altyapı gelişimi (0–10) ve lig seviyesi (leagues.level). Transfer bütçesi export'ta yok: NULL. Katalog dışı takımlara profil yazılmaz.
     */
    @Test
    void catalogImportFillsClubProfiles() throws Exception {
        Map<String, List<Map<String, String>>> tables = tables();
        importer.importMissing(tables, GAME_VERSION, false, true);
        Map<String, String> arsenal = tables.get("teams").stream().filter((r) -> "1".equals(r.get("teamid"))).findFirst().orElseThrow();
        Map<String, Object> profile = jdbc.queryForMap("""
                SELECT p.* FROM club_profile p JOIN club c ON c.id = p.club_id WHERE c.ea_team_id = 1 AND p.game_version_id = ?""", GAME_VERSION);
        assertThat(((Number) profile.get("domestic_prestige")).intValue()).isEqualTo(Integer.parseInt(arsenal.get("domesticprestige")));
        assertThat(((Number) profile.get("international_prestige")).intValue()).isEqualTo(Integer.parseInt(arsenal.get("internationalprestige")));
        assertThat(((Number) profile.get("popularity")).intValue()).isEqualTo(Integer.parseInt(arsenal.get("popularity")));
        assertThat(((Number) profile.get("youth_development")).intValue()).isEqualTo(Integer.parseInt(arsenal.get("youthdevelopment")));
        assertThat(((Number) profile.get("club_worth_eur")).longValue()).isEqualTo(Long.parseLong(arsenal.get("clubworth")) * 1000L);
        assertThat(((Number) profile.get("league_level")).intValue()).as("Premier League seviye 1").isEqualTo(1);
        assertThat(profile.get("transfer_budget_eur")).as("export'ta transfer bütçesi yok").isNull();

        Set<Integer> excluded = teamsInLeague(tables, 76);
        excluded.addAll(teamsInLeague(tables, 2028));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club_profile p JOIN club c ON c.id = p.club_id WHERE c.ea_team_id IN ("
                + excluded.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")", Integer.class)).isZero();
        // Yeniden içe aktarma güncellenir, çoğaltmaz.
        importer.importMissing(tables, GAME_VERSION, false, true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club_profile p JOIN club c ON c.id = p.club_id WHERE c.ea_team_id = 1", Integer.class)).isEqualTo(1);
    }

    /** Ana menü dışa aktarımında 2255 "Klasik Lig" (Classic XI, Soccer Aid, … XI): efsane takımları transfer edilemez, kataloğa girmez. */
    @Test
    void classicXiTeamsAndTheirLegendsStayOutOfTheCatalog() throws Exception {
        Map<String, List<Map<String, String>>> tables = tables();
        int legendTeam = 132_999;
        long legend = 199_999L;
        Map<String, String> template = tables.get("players").stream().filter(r -> Integer.parseInt(r.get("overallrating")) > 60).findFirst().orElseThrow();
        Map<String, String> legendRow = new HashMap<>(template);
        legendRow.put("playerid", String.valueOf(legend));
        legendRow.put("overallrating", "95");
        tables = new HashMap<>(tables);
        tables.put("players", append(tables.get("players"), legendRow));
        tables.put("teams", append(tables.get("teams"), Map.of("teamid", String.valueOf(legendTeam), "teamname", "Classic XI")));
        tables.put("leagues", append(tables.get("leagues"), Map.of("leagueid", "2255", "leaguename", "Klasik Lig")));
        tables.put("leagueteamlinks", append(tables.get("leagueteamlinks"), Map.of("teamid", String.valueOf(legendTeam), "leagueid", "2255")));
        tables.put("teamplayerlinks", append(tables.get("teamplayerlinks"), Map.of("playerid", String.valueOf(legend), "teamid", String.valueOf(legendTeam))));

        importer.importMissing(tables, GAME_VERSION, false, true);

        assertThat(clubsWithEaTeam(Set.of(legendTeam))).as("Klasik Lig takımı kulüp olmaz").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM base_player WHERE ea_resource_id = ?", Integer.class, legend)).as("efsane kataloğa girmez").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM league WHERE name = 'Klasik Lig' AND ea_league_id = 2255", Integer.class)).isZero();
    }

    private static List<Map<String, String>> append(List<Map<String, String>> rows, Map<String, String> row) {
        List<Map<String, String>> copy = new java.util.ArrayList<>(rows);
        copy.add(row);
        return copy;
    }

    @Test
    void freeAgentsAndRealClubsCarryEaIds() throws Exception {
        importer.importMissing(tables(), GAME_VERSION, false, true);

        for (int teamId : new int[]{111592, 131368}) {
            Map<String, Object> club = jdbc.queryForMap("SELECT c.name, l.name AS league, l.ea_league_id FROM club c JOIN league l ON l.id = c.league_id WHERE c.ea_team_id = ?", teamId);
            assertThat(club.get("league")).as("Serbest takım " + teamId).isEqualTo("Free Agents");
            assertThat(((Number) club.get("ea_league_id")).intValue()).isEqualTo(382);
        }
        assertThat(jdbc.queryForObject("SELECT gender FROM club WHERE ea_team_id = 131368", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM club WHERE ea_team_id = 1", String.class)).isEqualTo("Arsenal");
        assertThat(jdbc.queryForObject("SELECT name FROM club WHERE ea_team_id = 121", String.class)).isEqualTo("Crewe Alexandra");
        Integer leagueOfArsenal = jdbc.queryForObject("SELECT l.ea_league_id FROM club c JOIN league l ON l.id = c.league_id WHERE c.ea_team_id = 1", Integer.class);
        assertThat(leagueOfArsenal).isEqualTo(13);
    }

    @Test
    void importingTwiceDoesNotDuplicateClubsOrLeagues() throws Exception {
        importer.importMissing(tables(), GAME_VERSION, false, true);
        int clubs = jdbc.queryForObject("SELECT count(*) FROM club", Integer.class);
        int leagues = jdbc.queryForObject("SELECT count(*) FROM league", Integer.class);

        importer.importMissing(tables(), GAME_VERSION, false, true);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM club", Integer.class)).isEqualTo(clubs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM league", Integer.class)).isEqualTo(leagues);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club WHERE ea_team_id = 1", Integer.class)).isEqualTo(1);
    }
}
