package com.fcareer.ut.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UtCatalogJoinRepository {

    private static final String SQL = """
            SELECT b.ea_resource_id AS ea_id, b.id AS player_id, b.gender AS gender, n.name AS nation, c.name AS club, l.name AS league
            FROM base_player b
            LEFT JOIN nationality n ON n.id = b.nationality_id
            LEFT JOIN player_rating r ON r.base_player_id = b.id
                AND r.game_version_id = (SELECT MAX(game_version_id) FROM player_rating)
            LEFT JOIN club c ON c.id = r.club_id
            LEFT JOIN league l ON l.id = c.league_id
            WHERE b.ea_resource_id IN (:ids) AND b.owner_career_id IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public record CatalogInfo(long playerId, Integer gender, String nation, String club, String league) {
    }

    public Map<Long, CatalogInfo> findByEaResourceIds(Collection<Long> ids) {
        Map<Long, CatalogInfo> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query(SQL, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getLong("ea_id"), new CatalogInfo(rs.getLong("player_id"), rs.getInt("gender"), rs.getString("nation"),
                    rs.getString("club"), rs.getString("league")));
        });
        return result;
    }
}
