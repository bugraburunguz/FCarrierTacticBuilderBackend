package com.fcareer.development.calibration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.development.calibration.GrowthCalibration.SnapshotPlayer;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** FIT-3 madde 10: attribute'lu son IMPORT anlık görüntüleri (V25 öncesi satırlarda attrs NULL, sayılmaz). Yalnız okuma. */
@Repository
@RequiredArgsConstructor
public class GrowthCalibrationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** En yeni önce. */
    public List<Long> lastImportsWithAttrs(long careerId, int limit) {
        return jdbc.queryForList("""
                SELECT s.id FROM career_snapshot s
                WHERE s.career_id = :c AND s.source = 'IMPORT'
                  AND EXISTS (SELECT 1 FROM career_snapshot_player p WHERE p.snapshot_id = s.id AND p.attrs IS NOT NULL)
                ORDER BY s.id DESC LIMIT :n
                """, Map.of("c", careerId, "n", limit), Long.class);
    }

    public List<SnapshotPlayer> players(long snapshotId) {
        return jdbc.query("SELECT base_player_id, age, overall, team_id, attrs::text AS attrs FROM career_snapshot_player WHERE snapshot_id = :s AND attrs IS NOT NULL",
                Map.of("s", snapshotId), (rs, i) -> new SnapshotPlayer(rs.getLong("base_player_id"), (Integer) rs.getObject("age"), (Integer) rs.getObject("overall"),
                        attrs(rs.getString("attrs")), (Integer) rs.getObject("team_id")));
    }

    /** GROWTH-CAL: iki görüntü arasındaki oyun günü (oyun tarihi bilinmiyorsa null). */
    public Long intervalDays(long fromSnapshot, long toSnapshot) {
        return jdbc.queryForObject("""
                SELECT (SELECT game_date FROM career_snapshot WHERE id = :to) - (SELECT game_date FROM career_snapshot WHERE id = :from)""",
                Map.of("from", fromSnapshot, "to", toSnapshot), (rs, i) -> rs.getObject(1) == null ? null : rs.getLong(1));
    }

    /** GROWTH-CAL: kariyerin altyapı takımı (ACADEMY-a; bulunamadıysa null). */
    public Integer academyTeamId(long careerId) {
        return jdbc.queryForObject("SELECT academy_team_id FROM career WHERE id = :c", Map.of("c", careerId), Integer.class);
    }

    /** attrs JSONB anahtarları Attr sütun adı (sprint_speed ...); attribute olmayan alanlar (weak_foot, playstyles ...) atlanır. */
    private Map<Attr, Integer> attrs(String json) {
        Map<Attr, Integer> out = new EnumMap<>(Attr.class);
        try {
            JsonNode node = objectMapper.readTree(json);
            for (Attr attr : Attr.values()) {
                JsonNode v = node.get(attr.getColumn());
                if (Objects.nonNull(v) && v.isNumber()) {
                    out.put(attr, v.asInt());
                }
            }
        } catch (JsonProcessingException e) {
            return out;
        }
        return out;
    }
}
