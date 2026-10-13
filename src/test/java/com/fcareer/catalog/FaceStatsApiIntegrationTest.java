package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** F10: katalog yanıtı faceStats/faceLabels/faceStatsSource taşır; resmî değer varsa o, yoksa hesaplanan; kariyerde overlay'e göre. */
@IntegrationTest
class FaceStatsApiIntegrationTest {

    @Autowired
    private PlayerSearchRepository search;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    private long outfield;
    private long keeper;

    @BeforeEach
    void setUp() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        outfield = jdbc.queryForObject("SELECT base_player_id FROM player_rating WHERE game_version_id = 27 AND positions[1] <> 'GK' ORDER BY base_player_id LIMIT 1", Long.class);
        keeper = jdbc.queryForObject("SELECT base_player_id FROM player_rating WHERE game_version_id = 27 AND positions[1] = 'GK' ORDER BY base_player_id LIMIT 1", Long.class);
        jdbc.update("UPDATE player_rating SET face_stats = NULL WHERE base_player_id IN (?, ?)", outfield, keeper);
    }

    private PlayerSummaryResponse one(long id, Long careerId) {
        PlayerSearchRequest request = PlayerSearchRequest.builder().ids(List.of(id)).careerId(careerId).build();
        request.setGameVersionId((short) 27);
        request.setSize(5);
        return search.search(request).get(0);
    }

    @Test
    void anOfficialValueIsReturnedAsIs() {
        jdbc.update("UPDATE player_rating SET face_stats = ARRAY[11,22,33,44,55,66]::smallint[] WHERE base_player_id = ? AND game_version_id = 27", outfield);

        PlayerSummaryResponse player = one(outfield, null);

        assertThat(player.getFaceStats()).containsExactly(11, 22, 33, 44, 55, 66);
        assertThat(player.getFaceLabels()).containsExactly("PAC", "SHO", "PAS", "DRI", "DEF", "PHY");
        assertThat(player.getFaceStatsSource()).isEqualTo("OFFICIAL");
        assertThat(player.isFaceStatsApproximate()).isFalse();
    }

    @Test
    void withoutAnOfficialValueTheFormulaIsUsedAndMarkedApproximate() {
        PlayerSummaryResponse player = one(outfield, null);

        assertThat(player.getFaceStatsSource()).isEqualTo("COMPUTED");
        assertThat(player.getFaceStats()).hasSize(6);
        assertThat(player.isFaceStatsApproximate()).as("ağırlıklar verified:false").isTrue();
    }

    @Test
    void goalkeepersGetGoalkeeperLabels() {
        PlayerSummaryResponse player = one(keeper, null);

        assertThat(player.getFaceLabels()).containsExactly("DIV", "HAN", "KIC", "REF", "SPD", "POS");
    }

    @Test
    void careerOverlayAttributesDriveTheComputedValueAndStaleOfficialValuesAreIgnored() {
        jdbc.update("UPDATE player_rating SET face_stats = ARRAY[1,1,1,1,1,1]::smallint[], finishing = 50 WHERE base_player_id = ? AND game_version_id = 27", outfield);
        long userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, subscription_type) VALUES (?, 'x', 'FREE') RETURNING id", Long.class, "face-" + System.nanoTime() + "@example.com");
        int clubId = jdbc.queryForObject("SELECT id FROM club ORDER BY id LIMIT 1", Integer.class);
        long careerId = jdbc.queryForObject("INSERT INTO career (user_id, club_id, game_version_id, season) VALUES (?, ?, 27, 1) RETURNING id", Long.class, userId, clubId);
        jdbc.update("INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id) VALUES (?, ?, 70, 80, 1)", careerId, outfield);
        jdbc.update("INSERT INTO career_player_rating (career_id, base_player_id, game_version_id, overall, positions, finishing) VALUES (?, ?, 27, 70, ARRAY['ST'], 99)", careerId, outfield);

        Map<String, Integer> sho = Map.of("catalog", one(outfield, null).getFaceStats().get(1), "career", one(outfield, careerId).getFaceStats().get(1));

        assertThat(one(outfield, null).getFaceStatsSource()).isEqualTo("OFFICIAL");
        assertThat(one(outfield, careerId).getFaceStatsSource()).as("kariyer overlay'inde resmî değer yok: hesaplanır").isEqualTo("COMPUTED");
        assertThat(sho.get("career")).isGreaterThan(sho.get("catalog"));
    }
}
