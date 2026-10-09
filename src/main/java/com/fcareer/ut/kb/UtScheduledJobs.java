package com.fcareer.ut.kb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Zamanlanmış işler; çok örnekli çalışmada Postgres advisory lock ile tek örnek çalışır (ShedLock yerine bağımlılıksız). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcareer.ut.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UtScheduledJobs {

    private static final long PRICE_LOCK = 7_301_001L;
    private static final Duration PRICE_WINDOW = Duration.ofHours(3);

    private final KnowledgeBaseRepository repository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Scheduled(cron = "0 7 * * * *")
    public void aggregatePrices() {
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, PRICE_LOCK);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int buckets = repository.aggregatePrices(clock.instant().minus(PRICE_WINDOW));
            log.info("Price aggregation done buckets : {}", buckets);
        } finally {
            jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, PRICE_LOCK);
        }
    }

    static boolean enabled(Boolean value) {
        return Objects.nonNull(value) && value;
    }
}
