package com.fcareer.ingestion.base;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * E3-2 backfill: mevcut tüm kariyerlerin career_player satırlarını eski tablolardan tazeler (`fcareer.career.player-backfill-on-start=true`).
 * Veri silmez, upsert eder; her kariyer kendi işleminde. Eski tablolar yerinde kalır. Önce yedek alınmalıdır (backups/).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcareer.career.player-backfill-on-start", havingValue = "true")
public class CareerPlayerBackfillRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final CareerPlayerWriter writer;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> careers = jdbc.queryForList("SELECT id FROM career ORDER BY id", Long.class);
        for (Long careerId : careers) {
            Integer before = jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, careerId);
            int rows = new TransactionTemplate(transactionManager).execute((status) -> writer.syncCareer(careerId));
            log.info("career_player backfill careerId : {} before : {} upserted : {}", careerId, before, rows);
        }
        log.info("career_player backfill done careers : {}", careers.size());
    }
}
