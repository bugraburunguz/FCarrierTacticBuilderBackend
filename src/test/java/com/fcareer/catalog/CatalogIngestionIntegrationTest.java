package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.model.PageResponse;
import com.fcareer.ingestion.model.IngestionResult;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.roles.repository.BehaviorTagRepository;
import com.fcareer.roles.repository.RoleRepository;
import com.fcareer.tactics.repository.PresetRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@TestPropertySource(properties = {
        "fcareer.ingestion.provider=mock",
        "fcareer.ingestion.game-version-id=27",
        "fcareer.ingestion.mock.player-count=250"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogIngestionIntegrationTest {

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private PlayerRatingRepository playerRatingRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private BehaviorTagRepository behaviorTagRepository;
    @Autowired
    private PresetRepository presetRepository;

    @Test
    @Order(1)
    void referenceSeedIsPersistedOnStartup() {
        assertThat(roleRepository.count()).isGreaterThanOrEqualTo(37);
        assertThat(behaviorTagRepository.count()).isGreaterThan(40);
        assertThat(presetRepository.count()).isGreaterThanOrEqualTo(30);
    }

    @Test
    @Order(2)
    void ingestionCreatesThenUpdatesIdempotently() {
        IngestionResult first = ingestionService.run();
        IngestionResult second = ingestionService.run();

        assertThat(first.created()).isEqualTo(250);
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(250);
        assertThat(basePlayerRepository.count()).isEqualTo(250);
        assertThat(playerRatingRepository.count()).isEqualTo(250);
    }

    @Test
    @Order(3)
    void searchFiltersSortsAndPaginates() {
        PageResponse<PlayerSummaryResponse> all = playerService.search(PlayerSearchRequest.builder().size(10).build());
        assertThat(all.getTotalItems()).isEqualTo(250);
        assertThat(all.getItems()).hasSize(10);
        assertThat(all.getItems()).isSortedAccordingTo((a, b) -> Integer.compare(b.getOverall(), a.getOverall()));

        PageResponse<PlayerSummaryResponse> strikers = playerService.search(PlayerSearchRequest.builder()
                .positions(List.of("ST")).attrMin(Map.of("Finishing", 40)).ascending(true).sort("finishing").size(50).build());
        assertThat(strikers.getItems()).isNotEmpty().allSatisfy(p -> assertThat(p.getPositions()).contains("ST"));

        PageResponse<PlayerSummaryResponse> byName = playerService.search(PlayerSearchRequest.builder().query("player 17").build());
        assertThat(byName.getItems()).extracting(PlayerSummaryResponse::getName).contains("Mock Player 17");
    }

    @Test
    @Order(4)
    void detailExposesAttributesAndNoRealValuation() {
        Long id = playerService.search(PlayerSearchRequest.builder().size(1).build()).getItems().get(0).getId();

        PlayerDetailResponse detail = playerService.detail(id, null);

        assertThat(detail.getAttrs()).hasSize(34);
        assertThat(detail.getSummary().getPotential()).isNull();
        assertThat(detail.getSummary().isValuationEstimated()).isFalse();
        assertThat(detail.getSummary().getAccelerate()).isIn("EXPLOSIVE", "CONTROLLED", "LENGTHY");
    }

    @Test
    @Order(5)
    void fitPlayersMapAllAttributes() {
        List<Long> ids = playerService.search(PlayerSearchRequest.builder().size(3).build()).getItems()
                .stream().map(PlayerSummaryResponse::getId).toList();

        var players = playerService.fitPlayers(ids, null);

        assertThat(players).hasSize(3).allSatisfy(p -> assertThat(p.getAttrs()).hasSize(34));
    }
}
