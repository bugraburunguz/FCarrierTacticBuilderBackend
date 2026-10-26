package com.fcareer.development.track;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public void upsert(long careerId, LocalDate gameDate, String saveUid, int players, byte[] data) {
        jdbc.update("""
                INSERT INTO career_growth_snapshot (career_id, game_date, month_key, save_uid, player_count, data) VALUES (:c, :d, :m, :save, :n, :data)
                ON CONFLICT (career_id, month_key) DO UPDATE SET game_date = :d, save_uid = :save, player_count = :n, data = :data, created_at = now()""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("d", java.sql.Date.valueOf(gameDate)).addValue("m", monthKey(gameDate))
                        .addValue("save", saveUid).addValue("n", players).addValue("data", data));
    }

    /** Son yazılan görüntünün save kimliği (yoksa null). */
    public String lastSaveUid(long careerId) {
        // save_uid boş olabilir (eski betik, ana menü): Optional/findFirst null taşıyamaz, liste ilk eleman okunur.
        List<String> uids = jdbc.queryForList("SELECT save_uid FROM career_growth_snapshot WHERE career_id = :c ORDER BY created_at DESC, id DESC LIMIT 1",
                Map.of("c", careerId), String.class);
        return uids.isEmpty() ? null : uids.get(0);
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

    /**
     * GTR-b önbellek anahtarının parçası: kariyerin en son görüntü yazımı (upsert created_at = now()). Aynı ayın görüntüsü yeniden yazılınca (içe aktarma
     * sonra geri alınsa ve data_version artmasa da) değişir. Görüntü yoksa null.
     */
    public java.time.Instant lastWritten(long careerId) {
        List<java.sql.Timestamp> at = jdbc.queryForList("SELECT max(created_at) FROM career_growth_snapshot WHERE career_id = :c", Map.of("c", careerId),
                java.sql.Timestamp.class);
        return at.isEmpty() || Objects.isNull(at.get(0)) ? null : at.get(0).toInstant();
    }

    /** GTR-b: base_player kimliği → görüntüdeki EA kimliği (resmi oyuncu ea_resource_id; bu kariyerin üretilmiş oyuncusu career_source_id). */
    public Map<Long, Long> eaIds(long careerId, List<Long> basePlayerIds) {
        Map<Long, Long> out = new java.util.HashMap<>();
        if (basePlayerIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT id, CASE WHEN owner_career_id = :c THEN career_source_id ELSE ea_resource_id END AS ea FROM base_player
                 WHERE id IN (:ids) AND (owner_career_id IS NULL OR owner_career_id = :c)""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("ids", basePlayerIds), (rs) -> {
                    long ea = rs.getLong("ea");
                    if (!rs.wasNull()) {
                        out.put(rs.getLong("id"), ea);
                    }
                });
        return out;
    }
}
