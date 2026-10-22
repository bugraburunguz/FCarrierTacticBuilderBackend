package com.fcareer.usertactic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** `user_tactic` (V31, V43): kullanıcı başına, kariyer (ya da UT = null) başına 5 slot; kariyer başına en fazla bir ana taktik. */
@Repository
@RequiredArgsConstructor
public class UserTacticRepository {

    private static final String COLUMNS = "slot, career_id, name, formation, build_up, defensive_approach, line_height, slots::text AS slots, ea_code, source_replica_id, "
            + "is_main, lineup::text AS lineup, depth, preset_id, updated_at";
    private static final String SAME_OWNER = "user_id = ? AND COALESCE(career_id, 0) = COALESCE(?, 0)";
    private static final TypeReference<List<Map<String, Object>>> SLOTS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Long>> LINEUP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<UserTactic> list(long userId, Long careerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_tactic WHERE " + SAME_OWNER + " ORDER BY slot", rowMapper(), userId, careerId);
    }

    public Optional<UserTactic> find(long userId, Long careerId, int slot) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_tactic WHERE " + SAME_OWNER + " AND slot = ?", rowMapper(), userId, careerId, slot).stream().findFirst();
    }

    public Optional<UserTactic> findMain(long userId, long careerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_tactic WHERE user_id = ? AND career_id = ? AND is_main", rowMapper(), userId, careerId).stream().findFirst();
    }

    /** Üzerine kayıt is_main'i korur: ana taktik olan slota yeni sürüm yazılınca işaret düşmez. */
    public void upsert(long userId, UserTactic t) {
        jdbc.update("INSERT INTO user_tactic (user_id, career_id, slot, name, formation, build_up, defensive_approach, line_height, slots, ea_code, source_replica_id, "
                        + "lineup, depth, preset_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?) "
                        + "ON CONFLICT (user_id, (COALESCE(career_id, 0)), slot) DO UPDATE SET name = EXCLUDED.name, formation = EXCLUDED.formation, build_up = EXCLUDED.build_up, "
                        + "defensive_approach = EXCLUDED.defensive_approach, line_height = EXCLUDED.line_height, slots = EXCLUDED.slots, ea_code = EXCLUDED.ea_code, "
                        + "source_replica_id = EXCLUDED.source_replica_id, lineup = EXCLUDED.lineup, depth = EXCLUDED.depth, preset_id = EXCLUDED.preset_id, updated_at = now()",
                userId, t.careerId(), t.slot(), t.name(), t.formation(), t.buildUp(), t.defensiveApproach(), t.lineHeight(), write(t.slots()), t.eaCode(), t.sourceReplicaId(),
                Objects.isNull(t.lineup()) ? null : write(t.lineup()), t.depth(), t.presetId());
    }

    /** Kariyerin ana taktiğini bu slota taşır (önce eski işaret düşer: kısmi unique index). Slot yoksa 0. */
    public int setMain(long userId, long careerId, int slot) {
        jdbc.update("UPDATE user_tactic SET is_main = false WHERE user_id = ? AND career_id = ? AND is_main AND slot <> ?", userId, careerId, slot);
        return jdbc.update("UPDATE user_tactic SET is_main = true WHERE user_id = ? AND career_id = ? AND slot = ?", userId, careerId, slot);
    }

    public int delete(long userId, Long careerId, int slot) {
        return jdbc.update("DELETE FROM user_tactic WHERE " + SAME_OWNER + " AND slot = ?", userId, careerId, slot);
    }

    private RowMapper<UserTactic> rowMapper() {
        return (rs, i) -> new UserTactic(rs.getInt("slot"), (Long) rs.getObject("career_id"), rs.getString("name"), rs.getString("formation"), rs.getString("build_up"),
                rs.getString("defensive_approach"), (Integer) rs.getObject("line_height"), read(rs.getString("slots"), SLOTS), rs.getString("ea_code"),
                rs.getString("source_replica_id"), rs.getBoolean("is_main"), Objects.isNull(rs.getString("lineup")) ? null : read(rs.getString("lineup"), LINEUP),
                (Integer) rs.getObject("depth", Integer.class), rs.getString("preset_id"), rs.getObject("updated_at", OffsetDateTime.class), null);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
