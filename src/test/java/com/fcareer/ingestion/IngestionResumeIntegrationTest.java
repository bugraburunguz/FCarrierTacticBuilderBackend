package com.fcareer.ingestion;

import com.fcareer.IntegrationTest;
import com.fcareer.ingestion.provider.ExternalDataProvider;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@IntegrationTest
@TestPropertySource(properties = {"fcareer.ingestion.retry-backoff-millis=1", "fcareer.ingestion.page-size=100", "fcareer.ingestion.mock.player-count=250"})
class IngestionResumeIntegrationTest {

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private ExternalDataProvider provider;

    @Test
    void transientFailureIsRetriedAndRunCompletes() {
        jdbc.update("DELETE FROM ingestion_run");
        doThrow(new IllegalStateException("gecici")).doCallRealMethod().when(provider).fetch(eq(100), anyInt());

        var result = ingestionService.run();

        assertThat(result.created() + result.updated()).isGreaterThanOrEqualTo(250);
        Map<String, Object> run = jdbc.queryForMap("SELECT status, attempts FROM ingestion_run ORDER BY id DESC LIMIT 1");
        assertThat(run.get("status")).isEqualTo("DONE");
    }

    @Test
    void interruptedRunResumesFromTheLastOffset() {
        jdbc.update("DELETE FROM ingestion_run");
        doThrow(new IllegalStateException("kalici")).when(provider).fetch(eq(100), anyInt());

        assertThatThrownBy(() -> ingestionService.run()).isInstanceOf(IllegalStateException.class);
        Map<String, Object> failed = jdbc.queryForMap("SELECT status, next_offset FROM ingestion_run ORDER BY id DESC LIMIT 1");
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(((Number) failed.get("next_offset")).intValue()).isEqualTo(100);

        reset(provider);
        doCallRealMethod().when(provider).fetch(anyInt(), anyInt());
        ingestionService.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_run", Integer.class)).isEqualTo(1);
        Map<String, Object> done = jdbc.queryForMap("SELECT status, attempts FROM ingestion_run");
        assertThat(done.get("status")).isEqualTo("DONE");
        assertThat(((Number) done.get("attempts")).intValue()).isEqualTo(1);
    }
}
