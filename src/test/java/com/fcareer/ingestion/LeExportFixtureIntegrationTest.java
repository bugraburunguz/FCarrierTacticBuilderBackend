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
