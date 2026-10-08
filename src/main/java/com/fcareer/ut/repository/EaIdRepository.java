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

    /** Yakalanan kartlardan öğrenilen eşleşmeler: yalnızca henüz kimliği olmayan, adı tekil kayıtlara yazılır. */
    public int learnLeagues(Map<Integer, String> byEaId) {
        int[] total = {0};
        byEaId.forEach((id, name) -> total[0] += jdbc.update(
                "UPDATE league SET ea_league_id = :id WHERE name = :name AND ea_league_id IS NULL AND NOT EXISTS (SELECT 1 FROM league WHERE ea_league_id = :id)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)));
        return total[0];
    }

    public int learnNations(Map<Integer, String> byEaId) {
        int[] total = {0};
        byEaId.forEach((id, name) -> total[0] += jdbc.update(
                "UPDATE nationality SET ea_nation_id = :id WHERE name = :name AND ea_nation_id IS NULL AND NOT EXISTS (SELECT 1 FROM nationality WHERE ea_nation_id = :id)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)));
        return total[0];
    }
}
