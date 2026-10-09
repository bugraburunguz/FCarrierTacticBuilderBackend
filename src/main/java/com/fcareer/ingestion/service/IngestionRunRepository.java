package com.fcareer.ingestion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ingestion koşuları kalıcıdır: kesilen koşu aynı sağlayıcı için 24 saat içinde kaldığı offset'ten devam eder (BUG-09). */
@Repository
@RequiredArgsConstructor
public class IngestionRunRepository {

    public record Run(long id, int nextOffset, int created, int updated, int attempts) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<Run> findResumable(String provider) {
        return jdbc.query("SELECT id, next_offset, created, updated, attempts FROM ingestion_run WHERE provider = :p AND status IN ('RUNNING', 'FAILED') AND started_at > now() - interval '24 hours' ORDER BY id DESC LIMIT 1",
                Map.of("p", provider), (rs, i) -> new Run(rs.getLong("id"), rs.getInt("next_offset"), rs.getInt("created"), rs.getInt("updated"), rs.getInt("attempts"))).stream().findFirst();
    }

    public long start(String provider) {
        return jdbc.queryForObject("INSERT INTO ingestion_run (provider) VALUES (:p) RETURNING id", Map.of("p", provider), Long.class);
    }

    public void resume(long id) {
        jdbc.update("UPDATE ingestion_run SET status = 'RUNNING', attempts = attempts + 1, last_error = NULL WHERE id = :id", Map.of("id", id));
    }

    public void progress(long id, int nextOffset, int total, int created, int updated) {
        jdbc.update("UPDATE ingestion_run SET next_offset = :o, total = :t, created = :c, updated = :u WHERE id = :id",
                new MapSqlParameterSource().addValue("o", nextOffset).addValue("t", total).addValue("c", created).addValue("u", updated).addValue("id", id));
    }

    public void finish(long id) {
        jdbc.update("UPDATE ingestion_run SET status = 'DONE', finished_at = now() WHERE id = :id", Map.of("id", id));
    }

    public void fail(long id, String error) {
        jdbc.update("UPDATE ingestion_run SET status = 'FAILED', last_error = :e, finished_at = now() WHERE id = :id", Map.of("id", id, "e", error == null ? "" : error.substring(0, Math.min(error.length(), 500))));
    }

    public List<Map<String, Object>> recent() {
        return jdbc.queryForList("SELECT id, provider, status, next_offset, total, created, updated, attempts, last_error, started_at, finished_at FROM ingestion_run ORDER BY id DESC LIMIT 20", Map.of());
    }
}
