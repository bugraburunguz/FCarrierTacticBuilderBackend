package com.fcareer.fitengine;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.fitengine.level.FitLevelNormsBuilder;
import com.fcareer.fitengine.level.FitLevelNormsRepository;
import com.fcareer.fitengine.level.LevelFitService;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5: fit_level_norms üretimi (Flyway tablosu, bant kaydı, 101 noktalı artan dağılım, idempotent) ve levelFit sorgusu. */
@IntegrationTest
class FitLevelNormsIntegrationTest {

    private static final short GAME_VERSION = 27;

    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private FitLevelNormsBuilder builder;
    @Autowired
    private FitLevelNormsRepository repository;
    @Autowired
    private LevelFitService levelFitService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void rebuildWritesBandedAscendingDistributionsAndIsIdempotent() {
        int first = builder.rebuild(GAME_VERSION);
        assertThat(first).isPositive();
        List<FitLevelNormsRepository.Row> rows = repository.findByEngineVersion(FitEngineV3.VERSION);
        assertThat(rows).hasSize(first);
        for (FitLevelNormsRepository.Row row : rows) {
            assertThat(row.quantiles()).hasSize(101);
            assertThat(row.sampleCount()).isPositive();
            assertThat(row.bandLo()).isLessThanOrEqualTo(row.ovr());
            assertThat(row.bandHi()).isGreaterThanOrEqualTo(row.ovr());
            for (int i = 1; i < row.quantiles().length; i++) {
                assertThat(row.quantiles()[i]).isGreaterThanOrEqualTo(row.quantiles()[i - 1]);
            }
        }
        assertThat(builder.rebuild(GAME_VERSION)).isEqualTo(first);
        assertThat(repository.findByEngineVersion(FitEngineV3.VERSION)).hasSize(first);
    }

    @Test
    void levelFitIsBoundedAndMissingCombinationsAreEmpty() {
        builder.rebuild(GAME_VERSION);
        levelFitService.refresh();
        FitLevelNormsRepository.Row any = repository.findByEngineVersion(FitEngineV3.VERSION).get(0);
        LevelFitService.LevelFit low = levelFitService.levelFit(any.gender(), any.position(), any.roleId(), any.focus(), any.ovr(), 0).orElseThrow();
        LevelFitService.LevelFit high = levelFitService.levelFit(any.gender(), any.position(), any.roleId(), any.focus(), any.ovr(), 100).orElseThrow();
        assertThat(low.value()).isBetween(0.0, 100.0);
        assertThat(high.value()).isBetween(low.value(), 100.0);
        assertThat(levelFitService.levelFit(any.gender(), any.position(), "no_such_role", any.focus(), any.ovr(), 50)).isEmpty();
    }
}
