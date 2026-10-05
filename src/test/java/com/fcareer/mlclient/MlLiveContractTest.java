package com.fcareer.mlclient;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.mlclient.service.MlValuationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@EnabledIfSystemProperty(named = "ml.live", matches = "true")
class MlLiveContractTest {

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private MlValuationService mlValuationService;
    @Autowired
    private PlayerValuationRepository playerValuationRepository;
    @Autowired
    private BasePlayerRepository basePlayerRepository;

    @Test
    void javaFeaturesAreAcceptedByPythonServiceAndPersistedAsModeled() {
        ingestionService.run();

        int written = mlValuationService.run((short) 25);
        int rewritten = mlValuationService.run((short) 25);

        assertThat(written).isEqualTo(250);
        assertThat(rewritten).isEqualTo(250);
        List<PlayerValuationEntity> all = playerValuationRepository.findAll();
        assertThat(all).hasSize(250).allSatisfy(v -> {
            assertThat(v.getSource()).isEqualTo(ValuationSource.MODELED);
            assertThat(v.getOwnerUserId()).isNull();
            assertThat(v.getModelVersion()).startsWith("xgb-");
            assertThat(v.getPotential()).isBetween((short) 1, (short) 99);
            assertThat(v.getValueEur()).isGreaterThan(0);
        });
        assertThat(basePlayerRepository.count()).isEqualTo(250);
    }
}
