package com.fcareer.catalog.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.base.CareerPlayerWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Kariyer oyuncusunun tek doğruluk kaynağı (career_player) okuması: derece/aşinalık/özellikler. Katalog tablosuna dokunmaz. */
@Repository
@RequiredArgsConstructor
public class CareerPlayerRepository {

    /** career_player satırının motor ve detay ekranları için gereken kısmı. */
    public record Row(long playerId, int ovr, Integer pot, List<String> positions, Map<Attr, Integer> attrs, Map<String, Integer> playstyles, String accelerate,
                      Integer weakFoot, Integer skillMoves, int runStyle1, int runStyle2, List<Integer> faceStats, String bodyType, Map<String, Integer> roleFamiliarity,
                      List<String> hiddenTraits) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CareerPlayerWriter careerPlayerWriter;

    public Map<Long, Row> byIds(long careerId, Collection<Long> playerIds) {
        Map<Long, Row> out = new HashMap<>();
        if (playerIds.isEmpty()) {
            return out;
        }
        careerPlayerWriter.ensureSeeded(careerId);
        jdbc.query("""
                SELECT player_id, ovr, pot, positions, attrs, playstyles::text AS playstyles, accelerate, weak_foot, skill_moves, run_style1, run_style2, face_stats,
                       body_type, role_familiarity::text AS role_familiarity, hidden_traits
                  FROM career_player WHERE career_id = :c AND player_id IN (:ids)""", new MapSqlParameterSource("c", careerId).addValue("ids", playerIds), (rs) -> {
            try {
                Short[] attrValues = (Short[]) rs.getArray("attrs").getArray();
                Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
                Attr[] all = Attr.values();
                for (int i = 0; i < all.length && i < attrValues.length; i++) {
                    attrs.put(all[i], attrValues[i].intValue());
                }
                Array faces = rs.getArray("face_stats");
                Array traits = rs.getArray("hidden_traits");
                out.put(rs.getLong("player_id"), new Row(rs.getLong("player_id"), rs.getInt("ovr"), (Integer) rs.getObject("pot"),
                        Arrays.asList((String[]) rs.getArray("positions").getArray()), attrs, readMap(rs.getString("playstyles")), rs.getString("accelerate"),
                        (Integer) rs.getObject("weak_foot"), (Integer) rs.getObject("skill_moves"), rs.getInt("run_style1"), rs.getInt("run_style2"),
                        Objects.isNull(faces) ? null : Arrays.stream((Object[]) faces.getArray()).map((v) -> ((Number) v).intValue()).toList(), rs.getString("body_type"),
                        readMap(rs.getString("role_familiarity")), Objects.isNull(traits) ? List.of() : Arrays.asList((String[]) traits.getArray())));
            } catch (SQLException e) {
                throw new IllegalStateException("career_player unreadable", e);
            }
        });
        return out;
    }

    private Map<String, Integer> readMap(String json) {
        try {
            return Objects.isNull(json) ? Map.of() : objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (IOException e) {
            throw new IllegalStateException("career_player json", e);
        }
    }
}
