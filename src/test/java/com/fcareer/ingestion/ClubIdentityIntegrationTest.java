package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.provider.ExternalDataProvider;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

/** BE-BUG-15: kulüp EA team.id ile tanınır; lig değişince kopya satır açılmaz, ad/lig güncellenir. */
@IntegrationTest
@TestPropertySource(properties = {"fcareer.ingestion.page-size=100"})
class ClubIdentityIntegrationTest {

    private static final long TEAM_ID = 4_242_424L;

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private ExternalDataProvider provider;

    private static ExternalPlayerPage page(String league, String clubName) {
        ExternalPlayer player = new ExternalPlayer(88_000_001L, "Identity Test", LocalDate.of(2000, 1, 1), "Mockland", 180, 75, "Right",
                league, TEAM_ID, clubName, 70, List.of("ST"), 3, 3, Map.<Attr, Integer>of(), Map.of(), 0);
        return new ExternalPlayerPage(List.of(player), 1);
    }

    @Test
    void aClubThatChangesLeagueKeepsItsRowAndIsUpdated() {
        jdbc.update("DELETE FROM ingestion_run");
        doReturn(page("Championship", "Hull City")).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();
        Integer before = jdbc.queryForObject("SELECT id FROM club WHERE ea_team_id = ?", Integer.class, (int) TEAM_ID);

        jdbc.update("DELETE FROM ingestion_run");
        doReturn(page("Premier League", "Hull City AFC")).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM club WHERE ea_team_id = ?", Integer.class, (int) TEAM_ID)).isEqualTo(1);
        Map<String, Object> club = jdbc.queryForMap("SELECT c.id, c.name, l.name AS league FROM club c JOIN league l ON l.id = c.league_id WHERE c.ea_team_id = ?", (int) TEAM_ID);
        assertThat(club.get("id")).isEqualTo(before);
        assertThat(club.get("league")).isEqualTo("Premier League");
        assertThat(club.get("name")).isEqualTo("Hull City AFC");
    }
}
