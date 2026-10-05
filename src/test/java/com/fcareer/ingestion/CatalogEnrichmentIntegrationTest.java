package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.model.EnrichmentResult;
import com.fcareer.ingestion.service.CatalogEnrichmentService;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class CatalogEnrichmentIntegrationTest {

    private static final long FIRST_MOCK_ID = 900_000L;

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private CatalogEnrichmentService enrichmentService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void appliesHeightAndWeightFromTheDumpAndRecomputesAcceleRate() {
        String file = "playerid,height,weight,potential\n"
                + FIRST_MOCK_ID + ",201,95,99\n"
                + (FIRST_MOCK_ID + 1) + ",999,10,80\n"
                + "123456789,180,70,70\n";

        EnrichmentResult result = enrichmentService.enrichFromPlayersCsv(csv(file), (short) 27);

        assertThat(result.rowsInFile()).isEqualTo(3);
        assertThat(result.playersUpdated()).isEqualTo(2);
        BasePlayerEntity tall = basePlayerRepository.findByEaResourceId(FIRST_MOCK_ID).orElseThrow();
        assertThat(tall.getHeightCm()).isEqualTo((short) 201);
        assertThat(tall.getWeightKg()).isEqualTo((short) 95);
        assertThat(playerRatingRepository.findByBasePlayerIdAndGameVersionId(tall.getId(), (short) 27).orElseThrow()
                .getAccelerate()).isIn("EXPLOSIVE", "CONTROLLED", "LENGTHY");
    }

    @Test
    void ignoresImplausibleMeasurementsAndNeverReadsPotential() {
        BasePlayerEntity before = basePlayerRepository.findByEaResourceId(FIRST_MOCK_ID + 2).orElseThrow();
        Short heightBefore = before.getHeightCm();

        enrichmentService.enrichFromPlayersCsv(csv("playerid,height,potential\n" + (FIRST_MOCK_ID + 2) + ",999,99\n"), (short) 27);

        assertThat(basePlayerRepository.findByEaResourceId(FIRST_MOCK_ID + 2).orElseThrow().getHeightCm()).isEqualTo(heightBefore);
    }

    @Test
    void rejectsFilesWithoutTheRequiredColumns() {
        assertThatThrownBy(() -> enrichmentService.enrichFromPlayersCsv(csv("id,name\n1,x\n"), (short) 27))
                .isInstanceOf(ImportParseException.class);
    }
}
