package com.fcareer.ut;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.ingestion.service.CatalogIngestedEvent;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.ut.kb.UtItemBaseSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3-05d-b: ut_item temel kart senkronu (yalnız yerel katalogdan kopya, dış istek yok) kendiliğinden çalışır:
 * katalog ingestion bitince ve açılışta o oyun sürümü için ut_item boşsa. İdempotent.
 */
@IntegrationTest
@RecordApplicationEvents
class UtItemBaseSyncIntegrationTest {

    @Autowired
    private UtItemBaseSync sync;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private CatalogProperties catalogProperties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    private short gv;

    @BeforeEach
    void catalog() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        gv = catalogProperties.getDefaultGameVersion();
    }

    private int items() {
        return jdbc.queryForObject("SELECT count(*) FROM ut_item WHERE game_version_id = ? AND source = 'EA_SITE'", Integer.class, gv);
    }

    private int catalogCards() {
        return jdbc.queryForObject("SELECT count(*) FROM base_player b JOIN player_rating r ON r.base_player_id = b.id AND r.game_version_id = ? "
                + "WHERE b.ea_resource_id IS NOT NULL AND b.owner_career_id IS NULL", Integer.class, gv);
    }

    @Test
    void ingestionFinishingPublishesTheEventAndTheListenerSyncsTheCatalog() {
        ingestionService.run();

        assertThat(events.stream(CatalogIngestedEvent.class)).anySatisfy(e -> assertThat(e.gameVersionId()).isEqualTo(gv));
        assertThat(items()).isEqualTo(catalogCards()).isPositive();
    }

    @Test
    void syncIsIdempotent() {
        sync.sync(gv);
        int first = items();
        sync.sync(gv);
        assertThat(items()).isEqualTo(first).isEqualTo(catalogCards());
    }

    /** Açılış kancası: ut_item boşsa doldurur, doluysa dokunmaz. Silme test transaction'ında, geri alınır. */
    @Test
    @Transactional
    void startupSyncRunsOnlyWhenTheGameVersionHasNoItems() {
        sync.sync(gv);
        assertThat(sync.syncIfEmpty(gv)).isZero();

        jdbc.update("DELETE FROM ut_item WHERE game_version_id = ? AND NOT EXISTS (SELECT 1 FROM price_obs o WHERE o.ut_item_id = ut_item.id) "
                + "AND NOT EXISTS (SELECT 1 FROM price_agg a WHERE a.ut_item_id = ut_item.id)", gv);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ut_item WHERE game_version_id = ?", Integer.class, gv)).isZero();
        assertThat(sync.syncIfEmpty(gv)).isEqualTo(catalogCards());
        assertThat(items()).isEqualTo(catalogCards());
    }
}
