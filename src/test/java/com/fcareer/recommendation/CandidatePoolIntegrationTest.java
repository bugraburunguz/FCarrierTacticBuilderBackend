package com.fcareer.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static com.fcareer.CareerTestSupport.FIRST_MOCK_ID;
import static org.assertj.core.api.Assertions.assertThat;

/** E3-6 aday havuzu: önskor sıralaması (OVR değil), kayıt dosyası havuzu, kiralıktakiler ve edinilebilirlik kuralı (değer ≤ tavan VEYA serbest VEYA sözleşme ≤ 6 ay). */
@IntegrationTest
class CandidatePoolIntegrationTest {

    private static final short GAME_VERSION = 27;
    private static final int SPRINT = Attr.SPRINT_SPEED.ordinal() + 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerSearchRepository searchRepository;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
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

    private PlayerSearchRequest.PlayerSearchRequestBuilder pool(long careerId) {
        return PlayerSearchRequest.builder().gameVersionId(GAME_VERSION).careerId(careerId).size(50);
    }

    private int sprint(long careerId, long playerId) {
        return jdbc.queryForObject("SELECT attrs[" + SPRINT + "] FROM career_player WHERE career_id = ? AND player_id = ?", Integer.class, careerId, playerId);
    }

    @Test
    void prescoreOrdersByTheWeightedAttributesNotByOverall() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        List<PlayerSummaryResponse> result = searchRepository.search(pool(careerId).sort("prescore").prescoreWeights(Map.of(Attr.SPRINT_SPEED, 1.0)).build());
        assertThat(result).isNotEmpty();
        Integer best = jdbc.queryForObject("SELECT max(attrs[" + SPRINT + "]) FROM career_player WHERE career_id = ?", Integer.class, careerId);
        assertThat(sprint(careerId, result.get(0).getId())).as("önskor sıralaması en yüksek SprintSpeed'i başa alır").isEqualTo(best);
        for (int i = 1; i < result.size(); i++) {
            assertThat(sprint(careerId, result.get(i - 1).getId())).isGreaterThanOrEqualTo(sprint(careerId, result.get(i).getId()));
        }
    }

    @Test
    void anImportedCareersPoolIsOnlyThePlayersInTheSaveFile() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        support.importCsv(careerId, token, FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n" + (FIRST_MOCK_ID + 2) + ",77,80\n",
                FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n" + (FIRST_MOCK_ID + 2) + ",111592\n", null);
        Integer catalogOnly = jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ? AND NOT live", Integer.class, careerId);
        assertThat(catalogOnly).as("kayıtta olmayan katalog satırları var (ön koşul)").isPositive();

        List<PlayerSummaryResponse> pool = searchRepository.search(pool(careerId).livePoolOnly(true).build());
        assertThat(pool).extracting(PlayerSummaryResponse::getFaceAssetId).containsExactlyInAnyOrder(FIRST_MOCK_ID, FIRST_MOCK_ID + 1, FIRST_MOCK_ID + 2);
    }

    @Test
    void aCareerWithoutAnImportKeepsTheWholeCatalogAsItsPool() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        List<PlayerSummaryResponse> pool = searchRepository.search(pool(careerId).livePoolOnly(true).size(200).build());
        assertThat(pool.size()).isGreaterThan(100);
    }

    @Test
    void playersOnLoanAreNeverCandidates() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        support.importCsv(careerId, token, FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n", FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n", FIRST_MOCK_ID + ",99\n");
        List<PlayerSummaryResponse> pool = searchRepository.search(pool(careerId).livePoolOnly(true).availableOnly(true).build());
        assertThat(pool).extracting(PlayerSummaryResponse::getFaceAssetId).containsExactly(FIRST_MOCK_ID + 1);
    }

    @Test
    void acquirableMeansValueUnderTheCapOrFreeOrContractEndingSoon() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        support.importCsv(careerId, token, FIRST_MOCK_ID + ",91,95\n" + (FIRST_MOCK_ID + 1) + ",90,93\n" + (FIRST_MOCK_ID + 2) + ",77,80\n",
                FIRST_MOCK_ID + ",1\n" + (FIRST_MOCK_ID + 1) + ",1\n" + (FIRST_MOCK_ID + 2) + ",111592\n", null);
        jdbc.update("UPDATE career SET current_game_date = DATE '2027-07-01' WHERE id = ?", careerId);
        jdbc.update("UPDATE career_player SET contract_until = DATE '2027-09-01' WHERE career_id = ? AND player_id = (SELECT id FROM base_player WHERE ea_resource_id = ?)", careerId, FIRST_MOCK_ID + 1);

        List<PlayerSummaryResponse> pool = searchRepository.search(pool(careerId).livePoolOnly(true).acquirableValueCap(1L).contractMonthsMax(6).build());
        assertThat(pool).as("tavan 1 € iken yalnız serbest oyuncu ve sözleşmesi 6 ay içinde biten girer")
                .extracting(PlayerSummaryResponse::getFaceAssetId).containsExactlyInAnyOrder(FIRST_MOCK_ID + 1, FIRST_MOCK_ID + 2);
    }
}
