package com.fcareer.ingestion.base;

import com.fcareer.IntegrationTest;
import com.fcareer.career.model.request.CreateCareerRequest;
import com.fcareer.career.service.CareerService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private static final int CALLERS = 4;
    private static final int WAIT_SECONDS = 60;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CareerPlayerWriter writer;
    @Autowired
    private CareerService careerService;
    @Autowired
    private SquadVersionRepository squadVersions;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long userId;
    private final List<Long> careerIds = new ArrayList<>();

    @BeforeEach
    void createUser() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class,
                "seed-race-" + System.nanoTime() + "@test.local");
    }

    @AfterEach
    void deleteCareers() {
        for (Long careerId : careerIds) {
            careerService.delete(userId, careerId);
        }
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void concurrentFirstReadsInsideReadTransactionsSeedTheCareerOnce(CapturedOutput output) throws Exception {
        long careerId = insertBareCareer();
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    readOnly.executeWithoutResult((status) -> writer.ensureSeeded(careerId));
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
    void creatingACareerSeedsItsPlayersBeforeTheFirstRead() {
        Integer clubId = jdbc.queryForObject("""
                SELECT r.club_id FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                 WHERE r.game_version_id = 27 AND r.club_id IS NOT NULL AND b.owner_career_id IS NULL ORDER BY r.club_id LIMIT 1""", Integer.class);
        CreateCareerRequest request = new CreateCareerRequest();
        request.setClubId(clubId);
        request.setName("tohum");

        long careerId = careerService.create(userId, request).getId();
        careerIds.add(careerId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, careerId))
                .as("kariyer açılırken career_player dolu").isPositive();
    }

    private long insertBareCareer() {
        long squadVersionId = squadVersions.findLatest(GAME).orElseThrow().id();
        long careerId = jdbc.queryForObject("INSERT INTO career (user_id, game_version_id, name, squad_version_id) VALUES (?, 27, 'race', ?) RETURNING id",
                Long.class, userId, squadVersionId);
        careerIds.add(careerId);
        return careerId;
    }
}
