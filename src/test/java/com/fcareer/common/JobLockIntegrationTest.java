package com.fcareer.common;

import com.fcareer.IntegrationTest;
import com.fcareer.common.scheduling.JobLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JobLockIntegrationTest {

    private static final long KEY = 9_999_001L;

    @Autowired
    private JobLock jobLock;

    @Test
    void consecutiveRunsOnAPooledConnectionAreBothExecuted() {
        AtomicInteger runs = new AtomicInteger();

        assertThat(jobLock.runExclusive(KEY, runs::incrementAndGet)).isTrue();
        assertThat(jobLock.runExclusive(KEY, runs::incrementAndGet)).isTrue();

        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    void aSecondInstanceSkipsWhileTheFirstHoldsTheLockAndTheLockIsReleasedAfterwards() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = pool.submit(() -> jobLock.runExclusive(KEY, () -> {
                holding.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(jobLock.runExclusive(KEY, () -> { })).isFalse();

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jobLock.runExclusive(KEY, () -> { })).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
