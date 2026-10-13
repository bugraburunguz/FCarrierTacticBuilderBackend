package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.provider.ExternalDataProvider;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** BE-BUG-16: tek RUNNING koşu (DB), heartbeat ile bayat koşu devri, total yoksa DONE yok, tam koşuda görülmeyen oyuncu emekliye ayrılır. */
@IntegrationTest
@TestPropertySource(properties = {"fcareer.ingestion.page-size=100", "fcareer.ingestion.retry-attempts=1"})
class IngestionLifecycleIntegrationTest {

    private static final long BASE = 77_000_000L;

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private ExternalDataProvider provider;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ingestion_run");
    }

    private static ExternalPlayer player(long id) {
        return new ExternalPlayer(id, "Lifecycle " + id, LocalDate.of(2000, 1, 1), "Mockland", 180, 75, "Right",
                "Mock League", 9_000_001L, "Lifecycle FC", 70, List.of("ST"), 3, 3, Map.<Attr, Integer>of(), Map.of(), 0, null);
    }

    private static ExternalPlayerPage page(int count, int total) {
        return new ExternalPlayerPage(IntStream.range(0, count).mapToObj(i -> player(BASE + i)).toList(), total);
    }

    @Test
    void aLiveRunningRunBlocksAnotherAndIsNotResumed() {
        jdbc.update("INSERT INTO ingestion_run (provider, status, heartbeat_at) VALUES (?, 'RUNNING', now())", provider.name());

        var result = ingestionService.run();

        assertThat(result.skipped()).isTrue();
        verify(provider, never()).fetch(anyInt(), anyInt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_run", Integer.class)).isEqualTo(1);
    }

    @Test
    void aRunningRunWithAnExpiredHeartbeatIsFailedAndResumed() {
        doReturn(page(20, 20)).when(provider).fetch(anyInt(), anyInt());
        Long stale = jdbc.queryForObject("INSERT INTO ingestion_run (provider, status, heartbeat_at) VALUES (?, 'RUNNING', now() - interval '2 hours') RETURNING id", Long.class, provider.name());

        var result = ingestionService.run();

        assertThat(result.skipped()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM ingestion_run WHERE id = ?", String.class, stale)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_run", Integer.class)).isEqualTo(1);
    }

    @Test
    void aPageWithoutATotalDoesNotMarkTheRunDone() {
        doReturn(page(5, 0)).when(provider).fetch(anyInt(), anyInt());

        assertThatThrownBy(() -> ingestionService.run()).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM ingestion_run", String.class)).isEqualTo("FAILED");
    }

    @Test
    void playersMissingFromAFullRunAreRetiredAndHiddenFromBrowsing() {
        doReturn(page(20, 20)).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();
        doReturn(page(19, 19)).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();

        Long gone = jdbc.queryForObject("SELECT id FROM base_player WHERE ea_resource_id = ?", Long.class, BASE + 19);
        assertThat(jdbc.queryForObject("SELECT retired_at IS NOT NULL FROM base_player WHERE id = ?", Boolean.class, gone)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM base_player WHERE ea_resource_id BETWEEN ? AND ? AND retired_at IS NULL", Integer.class, BASE, BASE + 18)).isEqualTo(19);
    }

    @Test
    void aSuddenlyTinyCatalogDoesNotRetireEveryone() {
        doReturn(page(20, 20)).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();
        doReturn(page(2, 2)).when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM base_player WHERE ea_resource_id BETWEEN ? AND ? AND retired_at IS NOT NULL", Integer.class, BASE, BASE + 19)).isZero();
    }
}
