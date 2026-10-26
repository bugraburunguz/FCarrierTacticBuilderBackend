package com.fcareer.development.track;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GTR-a: career_growth_snapshot (V49). Aynı oyun ayı tek satır: yeni içe aktarma o ayın görüntüsünü değiştirir. */
@Repository
@RequiredArgsConstructor
public class GrowthTrackRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record SnapshotRef(long id, LocalDate gameDate) {
    }

    public record Snapshot(long id, LocalDate gameDate, byte[] data) {
    }

    public static int monthKey(LocalDate date) {
        return date.getYear() * 100 + date.getMonthValue();
    }

    public void upsert(long careerId, LocalDate gameDate, int players, byte[] data) {
        jdbc.update("""
                INSERT INTO career_growth_snapshot (career_id, game_date, month_key, player_count, data) VALUES (:c, :d, :m, :n, :data)
                ON CONFLICT (career_id, month_key) DO UPDATE SET game_date = :d, player_count = :n, data = :data, created_at = now()""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("d", java.sql.Date.valueOf(gameDate)).addValue("m", monthKey(gameDate))
                        .addValue("n", players).addValue("data", data));
    }

    /** Oyun tarihine göre eskiden yeniye. */
    public List<SnapshotRef> refs(long careerId) {
        return jdbc.query("SELECT id, game_date FROM career_growth_snapshot WHERE career_id = :c ORDER BY game_date, id", Map.of("c", careerId),
                (rs, i) -> new SnapshotRef(rs.getLong("id"), rs.getDate("game_date").toLocalDate()));
    }

    public void delete(List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("DELETE FROM career_growth_snapshot WHERE id IN (:ids)", Map.of("ids", ids));
        }
    }

    public Optional<Snapshot> load(long id) {
        return jdbc.query("SELECT id, game_date, data FROM career_growth_snapshot WHERE id = :id", Map.of("id", id),
                (rs, i) -> new Snapshot(rs.getLong("id"), rs.getDate("game_date").toLocalDate(), rs.getBytes("data"))).stream().findFirst();
    }
}
