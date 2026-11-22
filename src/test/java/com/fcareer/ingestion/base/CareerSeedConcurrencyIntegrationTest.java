package com.fcareer.ingestion.base;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CareerSeedConcurrencyIntegrationTest {

    private static final short GAME = 27;
    private static final int CALLERS = 6;
    private static final int WAIT_SECONDS = 60;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CareerPlayerWriter writer;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    private Long userId;
    private Long careerId;

    @BeforeEach
    void createCareer() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        long squadVersionId = squadVersions.findLatest(GAME).orElseThrow().id();
        userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class,
                "seed-race-" + System.nanoTime() + "@test.local");
        careerId = jdbc.queryForObject("INSERT INTO career (user_id, game_version_id, name, squad_version_id) VALUES (?, 27, 'race', ?) RETURNING id",
                Long.class, userId, squadVersionId);
    }

    @AfterEach
    void deleteCareer() {
        jdbc.update("DELETE FROM career_player WHERE career_id = ?", careerId);
        jdbc.update("DELETE FROM career WHERE id = ?", careerId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void concurrentFirstReadsSeedTheCareerOnce(CapturedOutput output) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    writer.ensureSeeded(careerId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> call : calls) {
                call.get(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long syncs = Pattern.compile("career_player synced careerId : " + careerId + " ").matcher(output.getOut()).results().count();
        assertThat(syncs).as("tohumlama sayısı").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, careerId)).isPositive();
    }

    @Test
    void seedingRefreshesTheCareerPlayerStatistics() {
        Timestamp before = lastAnalyze();

        writer.ensureSeeded(careerId);

        Timestamp after = lastAnalyze();
        assertThat(after).as("career_player istatistiği tohumlamadan sonra toplanır").isNotNull();
        if (before != null) {
            assertThat(after).isAfter(before);
        }
    }

    private Timestamp lastAnalyze() {
        return jdbc.queryForObject("SELECT last_analyze FROM pg_stat_user_tables WHERE relname = 'career_player'", Timestamp.class);
    }
}
