package com.fcareer.common.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Çok örnekli çalışmada zamanlanmış işi tek örneğe indirir (BE-SEC-04). Kilit transaction ömürlüdür (pg_try_advisory_xact_lock):
 * lock ve unlock'un farklı havuz bağlantılarına düşüp kilidi asılı bırakması mümkün değildir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobLock {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    /** Kilit alınırsa işi çalıştırır ve true döner; başka örnek tutuyorsa atlar ve false döner. */
    public boolean runExclusive(long key, Runnable job) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, key);
            if (!Boolean.TRUE.equals(locked)) {
                log.info("Job skipped, lock held by another instance key : {}", key);
                return false;
            }
            Objects.requireNonNull(job).run();
            return true;
        }));
    }
}
