package com.fcareer.usertactic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** `user_tactic` (V31): kullanıcı başına, kariyer (ya da UT = null) başına 5 slot. */
@Repository
@RequiredArgsConstructor
public class UserTacticRepository {

    private static final String COLUMNS = "slot, career_id, name, formation, build_up, defensive_approach, line_height, slots::text AS slots, ea_code, source_replica_id";
    private static final TypeReference<List<Map<String, String>>> SLOTS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<UserTactic> list(long userId, Long careerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_tactic WHERE user_id = ? AND COALESCE(career_id, 0) = COALESCE(?, 0) ORDER BY slot",
                (rs, i) -> new UserTactic(rs.getInt("slot"), (Long) rs.getObject("career_id"), rs.getString("name"), rs.getString("formation"), rs.getString("build_up"),
                        rs.getString("defensive_approach"), (Integer) rs.getObject("line_height"), readSlots(rs.getString("slots")), rs.getString("ea_code"), rs.getString("source_replica_id")),
                userId, careerId);
    }

    public void upsert(long userId, UserTactic t) {
        jdbc.update("INSERT INTO user_tactic (user_id, career_id, slot, name, formation, build_up, defensive_approach, line_height, slots, ea_code, source_replica_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) "
                        + "ON CONFLICT (user_id, (COALESCE(career_id, 0)), slot) DO UPDATE SET name = EXCLUDED.name, formation = EXCLUDED.formation, build_up = EXCLUDED.build_up, "
                        + "defensive_approach = EXCLUDED.defensive_approach, line_height = EXCLUDED.line_height, slots = EXCLUDED.slots, ea_code = EXCLUDED.ea_code, source_replica_id = EXCLUDED.source_replica_id",
                userId, t.careerId(), t.slot(), t.name(), t.formation(), t.buildUp(), t.defensiveApproach(), t.lineHeight(), writeSlots(t.slots()), t.eaCode(), t.sourceReplicaId());
    }

    public int delete(long userId, Long careerId, int slot) {
        return jdbc.update("DELETE FROM user_tactic WHERE user_id = ? AND COALESCE(career_id, 0) = COALESCE(?, 0) AND slot = ?", userId, careerId, slot);
    }

    private List<Map<String, String>> readSlots(String json) {
        try {
            return mapper.readValue(json, SLOTS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String writeSlots(List<Map<String, String>> slots) {
        try {
            return mapper.writeValueAsString(slots);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
