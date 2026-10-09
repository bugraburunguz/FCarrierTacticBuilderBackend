package com.fcareer.ut.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** EA kimliği → katalog adı eşlemeleri (SBC şartlarını bizim kulüp/lig/ülke adlarımıza çevirmek için). */
@Repository
@RequiredArgsConstructor
public class EaIdRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Integer, String> clubNames(Collection<Integer> eaIds) {
        return names("SELECT ea_team_id AS ea, name FROM club WHERE ea_team_id IN (:ids) ORDER BY id DESC", eaIds);
    }

    public Map<Integer, String> leagueNames(Collection<Integer> eaIds) {
        return names("SELECT ea_league_id AS ea, name FROM league WHERE ea_league_id IN (:ids)", eaIds);
    }

    public Map<Integer, String> nationNames(Collection<Integer> eaIds) {
        return names("SELECT ea_nation_id AS ea, name FROM nationality WHERE ea_nation_id IN (:ids)", eaIds);
    }

    private Map<Integer, String> names(String sql, Collection<Integer> eaIds) {
        Map<Integer, String> result = new HashMap<>();
        if (eaIds.isEmpty()) {
            return result;
        }
        jdbc.query(sql, new MapSqlParameterSource("ids", eaIds), rs -> {
            result.putIfAbsent(rs.getInt("ea"), rs.getString("name"));
        });
        return result;
    }

    /** Bağımsız kullanıcı sayısı bu eşiğe ulaşınca öneri kalıcı yazılır (BUG-11); kimliği olmayan çağrı oy sayılmaz. */
    public static final int APPLY_THRESHOLD = 3;

    public int learnLeagues(Map<Integer, String> byEaId, Long userId) {
        return suggest("LEAGUE", byEaId, userId);
    }

    public int learnNations(Map<Integer, String> byEaId, Long userId) {
        return suggest("NATION", byEaId, userId);
    }

    private int suggest(String kind, Map<Integer, String> byEaId, Long userId) {
        if (java.util.Objects.isNull(userId)) {
            return 0;
        }
        int[] applied = {0};
        byEaId.forEach((id, name) -> {
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("k", kind).addValue("id", id).addValue("n", name).addValue("u", userId);
            jdbc.update("""
                    INSERT INTO ea_id_suggestion (kind, ea_id, name, votes, voters) VALUES (:k, :id, :n, 1, ARRAY[CAST(:u AS BIGINT)])
                    ON CONFLICT (kind, ea_id, name) DO UPDATE SET
                      votes = CASE WHEN CAST(:u AS BIGINT) = ANY(ea_id_suggestion.voters) THEN ea_id_suggestion.votes ELSE ea_id_suggestion.votes + 1 END,
                      voters = CASE WHEN CAST(:u AS BIGINT) = ANY(ea_id_suggestion.voters) THEN ea_id_suggestion.voters ELSE array_append(ea_id_suggestion.voters, CAST(:u AS BIGINT)) END
                    """, params);
            Integer votes = jdbc.queryForObject("SELECT votes FROM ea_id_suggestion WHERE kind = :k AND ea_id = :id AND name = :n AND NOT applied", params, Integer.class);
            if (votes != null && votes >= APPLY_THRESHOLD) {
                applied[0] += apply(kind, id, name);
            }
        });
        return applied[0];
    }

    /** Admin onayı ya da eşik aşımı: yalnız henüz kimliği olmayan ve adı tekil kayda yazılır. */
    public int apply(String kind, int eaId, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", eaId).addValue("name", name).addValue("k", kind);
        int updated = "LEAGUE".equals(kind)
                ? jdbc.update("UPDATE league SET ea_league_id = :id WHERE name = :name AND ea_league_id IS NULL AND NOT EXISTS (SELECT 1 FROM league WHERE ea_league_id = :id)", params)
                : jdbc.update("UPDATE nationality SET ea_nation_id = :id WHERE name = :name AND ea_nation_id IS NULL AND NOT EXISTS (SELECT 1 FROM nationality WHERE ea_nation_id = :id)", params);
        jdbc.update("UPDATE ea_id_suggestion SET applied = true WHERE kind = :k AND ea_id = :id AND name = :name", params);
        return updated;
    }

    public java.util.List<Map<String, Object>> openSuggestions() {
        return jdbc.queryForList("SELECT id, kind, ea_id, name, votes FROM ea_id_suggestion WHERE NOT applied ORDER BY votes DESC, id", Map.of());
    }
}
