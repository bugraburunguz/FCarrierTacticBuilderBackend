package com.fcareer.ut.kb;

import com.fcareer.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Zamanlanmış işler; çok örnekli çalışmada transaction ömürlü Postgres advisory lock ile tek örnek çalışır (JobLock). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcareer.ut.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UtScheduledJobs {

    private static final long PRICE_LOCK = 7_301_001L;
    private static final Duration PRICE_WINDOW = Duration.ofHours(3);

    private final KnowledgeBaseRepository repository;
    private final JobLock jobLock;
    private final Clock clock;

    @Scheduled(cron = "0 7 * * * *")
    public void aggregatePrices() {
        jobLock.runExclusive(PRICE_LOCK, () -> {
            int buckets = repository.aggregatePrices(clock.instant().minus(PRICE_WINDOW));
            log.info("Price aggregation done buckets : {}", buckets);
        });
    }
}
