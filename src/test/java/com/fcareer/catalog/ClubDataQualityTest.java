package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.alias.ClubDataQuality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3-04.1: diakritik düşmesi ve yer tutucu adların tekrarını yakalayan veri kalitesi kuralları.
 * Eklenen oyuncusuz kulüp/lig satırları test sonunda geri alınır; paylaşılan context'teki diğer testlere sızmaz.
 */
@IntegrationTest
@Transactional
class ClubDataQualityTest {

    @Autowired
    private ClubDataQuality quality;
    @Autowired
    private JdbcTemplate jdbc;

    private Integer league(String name) {
        jdbc.update("INSERT INTO league (name) VALUES (?) ON CONFLICT DO NOTHING", name);
        return jdbc.queryForObject("SELECT id FROM league WHERE name = ?", Integer.class, name);
    }

    @Test
    void aDroppedAccentTwinInTheSameLeagueIsReported() {
        Integer league = league("Quality League A");
        jdbc.update("INSERT INTO club (name, league_id, gender) VALUES ('Club Len Q', ?, 0), ('Club León Q', ?, 0)", league, league);

        assertThat(quality.violations()).anyMatch((v) -> v.contains("Club Len Q") || v.contains("Club León Q"));
    }

    @Test
    void theSameNameInADifferentLeagueOrGenderIsNotATwin() {
        Integer a = league("Quality League B1");
        Integer b = league("Quality League B2");
        jdbc.update("INSERT INTO club (name, league_id, gender) VALUES ('Quartier FC', ?, 0), ('Quartier FC', ?, 1)", a, b);

        assertThat(quality.violations()).noneMatch((v) -> v.contains("Quartier"));
    }

    @Test
    void replacementCharactersAndPlaceholderNamesAreReported() {
        Integer league = league("Quality League C");
        jdbc.update("INSERT INTO club (name, league_id, gender) VALUES (?, ?, 0), (?, ?, 0)", "Bozuk \uFFFD Kulüp", league, "*TeamName_Abbr15_999", league);

        assertThat(quality.violations()).anyMatch((v) -> v.contains("U+FFFD")).anyMatch((v) -> v.contains("yer tutucu"));
    }

    @Test
    void theIngestionRejectsPlaceholderClubNames() {
        assertThat(ClubDataQuality.isPlaceholder("*TeamName_Abbr15_112264")).isTrue();
        assertThat(ClubDataQuality.isPlaceholder("Real Madrid")).isFalse();
    }
}
