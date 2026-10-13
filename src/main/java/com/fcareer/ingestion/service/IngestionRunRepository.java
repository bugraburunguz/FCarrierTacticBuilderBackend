package com.fcareer.ingestion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ingestion koşuları kalıcıdır. Provider başına tek RUNNING satır kısmi tekil indeksle (V26) garanti edilir; böylece birden çok
 * örnek ya da thread aynı anda koşamaz. Koşan iş her sayfada heartbeat yazar; heartbeat'i bayatlayan koşu FAILED sayılıp
 * 24 saat içinde kaldığı offset'ten devam eder (BUG-09, BE-BUG-16).
 */
@Repository
@RequiredArgsConstructor
public class IngestionRunRepository {

    public record Run(long id, int nextOffset, int created, int updated, int attempts) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Heartbeat'i bayatlamış RUNNING koşuları FAILED yapar (çöken örnek). */
    public int expireStale(String provider, Duration staleAfter) {
        return jdbc.update("UPDATE ingestion_run SET status = 'FAILED', last_error = 'heartbeat expired', finished_at = now() WHERE provider = :p AND status = 'RUNNING' AND heartbeat_at < now() - make_interval(secs => :s)",
                new MapSqlParameterSource().addValue("p", provider).addValue("s", (double) staleAfter.toSeconds()));
    }

    public Optional<Run> findResumable(String provider) {
        return jdbc.query("SELECT id, next_offset, created, updated, attempts FROM ingestion_run WHERE provider = :p AND status = 'FAILED' AND started_at > now() - interval '24 hours' ORDER BY id DESC LIMIT 1",
                Map.of("p", provider), (rs, i) -> new Run(rs.getLong("id"), rs.getInt("next_offset"), rs.getInt("created"), rs.getInt("updated"), rs.getInt("attempts"))).stream().findFirst();
    }

    /** Yeni koşu açar; başka bir RUNNING koşu varsa boş döner. */
    public Optional<Long> start(String provider) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("INSERT INTO ingestion_run (provider) VALUES (:p) RETURNING id", Map.of("p", provider), Long.class));
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    /** FAILED koşuyu sahiplenir; başka bir RUNNING koşu varsa ya da satır artık FAILED değilse false. */
    public boolean resume(long id) {
        try {
            return jdbc.update("UPDATE ingestion_run SET status = 'RUNNING', attempts = attempts + 1, last_error = NULL, finished_at = NULL, heartbeat_at = now() WHERE id = :id AND status = 'FAILED'", Map.of("id", id)) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void progress(long id, int nextOffset, int total, int created, int updated) {
        jdbc.update("UPDATE ingestion_run SET next_offset = :o, total = :t, created = :c, updated = :u, heartbeat_at = now() WHERE id = :id",
                new MapSqlParameterSource().addValue("o", nextOffset).addValue("t", total).addValue("c", created).addValue("u", updated).addValue("id", id));
    }

    public void finish(long id) {
        jdbc.update("UPDATE ingestion_run SET status = 'DONE', finished_at = now() WHERE id = :id", Map.of("id", id));
    }

    public void fail(long id, String error) {
        jdbc.update("UPDATE ingestion_run SET status = 'FAILED', last_error = :e, finished_at = now() WHERE id = :id", Map.of("id", id, "e", error == null ? "" : error.substring(0, Math.min(error.length(), 500))));
    }

    public List<Map<String, Object>> recent() {
        return jdbc.queryForList("SELECT id, provider, status, next_offset, total, created, updated, attempts, last_error, started_at, finished_at, heartbeat_at FROM ingestion_run ORDER BY id DESC LIMIT 20", Map.of());
    }
}
