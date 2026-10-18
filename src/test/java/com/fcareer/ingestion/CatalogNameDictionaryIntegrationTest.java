package com.fcareer.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FC27 dışa aktarımında isim tablosu (playernames) yok: kariyerde üretilen oyuncu "Oyuncu #…" kalıyordu. Oyunda ilk ad, soyad ve lakap kimlikleri
 * tek bir isim tablosuna işaret eder; katalogda adını bildiğimiz oyuncuların kimliklerinden sözlük çıkarılır. Yeniden içe aktarmada önceden adsız
 * kalan kariyer oyuncusu adlanır.
 */
@IntegrationTest
class CatalogNameDictionaryIntegrationTest {

    private static final short GV = 27;

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
    private BaseCatalogImporter importer;
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

    private static Map<String, String> player(long id, int first, int last, int common) {
        Map<String, String> row = new HashMap<>();
        row.put("playerid", String.valueOf(id));
        row.put("overallrating", "68");
        row.put("potential", "75");
        row.put("preferredposition1", "14");
        row.put("firstnameid", String.valueOf(first));
        row.put("lastnameid", String.valueOf(last));
        row.put("commonnameid", String.valueOf(common));
        return row;
    }

    private String nameOf(long careerId, long sourceId) {
        return jdbc.queryForObject("SELECT full_name FROM base_player WHERE owner_career_id = ? AND career_source_id = ?", String.class, careerId, sourceId);
    }

    @Test
    void unknownPlayersAreNamedFromTheCatalogsNameIds() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        List<Long> known = jdbc.queryForList("SELECT ea_resource_id FROM base_player WHERE owner_career_id IS NULL AND ea_resource_id >= 900000 ORDER BY id LIMIT 2", Long.class);
        String knownA = jdbc.queryForObject("SELECT full_name FROM base_player WHERE ea_resource_id = ? AND owner_career_id IS NULL", String.class, known.get(0));
        String knownB = jdbc.queryForObject("SELECT full_name FROM base_player WHERE ea_resource_id = ? AND owner_career_id IS NULL", String.class, known.get(1));
        String firstA = knownA.split(" ")[0];
        String lastB = knownB.substring(knownB.indexOf(' ') + 1);
        long mixed = 7_100_001L;
        long lastOnly = 7_100_002L;
        long hopeless = 7_100_003L;

        Map<String, List<Map<String, String>>> tables = Map.of(
                "players", List.of(player(known.get(0), 501, 601, 0), player(known.get(1), 502, 602, 0),
                        player(mixed, 501, 602, 0), player(lastOnly, 999, 602, 0), player(hopeless, 998, 997, 0)),
                "teams", List.of(Map.of("teamid", "7100", "teamname", "Sözlük FC")),
                "teamplayerlinks", List.of(Map.of("playerid", String.valueOf(mixed), "teamid", "7100"), Map.of("playerid", String.valueOf(lastOnly), "teamid", "7100"),
                        Map.of("playerid", String.valueOf(hopeless), "teamid", "7100")));

        importer.upsertCareerPlayers(careerId, tables, GV);

        assertThat(nameOf(careerId, mixed)).isEqualTo(firstA + " " + lastB);
        assertThat(nameOf(careerId, lastOnly)).isEqualTo(lastB);
        assertThat(nameOf(careerId, hopeless)).isEqualTo("Oyuncu #" + hopeless);

        // Önceki (sözlüksüz) içe aktarmadan adsız kalmış oyuncu yeniden içe aktarmada adlanır.
        jdbc.update("UPDATE base_player SET full_name = ? WHERE owner_career_id = ? AND career_source_id = ?", "Oyuncu #" + mixed, careerId, mixed);
        importer.upsertCareerPlayers(careerId, tables, GV);
        assertThat(nameOf(careerId, mixed)).isEqualTo(firstA + " " + lastB);
    }
}
