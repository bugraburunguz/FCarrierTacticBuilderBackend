package com.fcareer.catalog.repository;

import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class PlayerSearchRepository {

    private static final String FROM = """
             FROM player_rating r
             JOIN base_player b ON b.id = r.base_player_id
             LEFT JOIN club c ON c.id = r.club_id
             LEFT JOIN league l ON l.id = c.league_id
             LEFT JOIN nationality n ON n.id = b.nationality_id
             LEFT JOIN player_valuation v ON v.base_player_id = r.base_player_id
                  AND v.game_version_id = r.game_version_id AND v.source = 'MODELED' AND v.owner_user_id IS NULL
            """;
    private static final String SELECT = """
            SELECT b.id AS id, b.full_name AS name, b.dob AS dob, r.overall AS overall, r.positions AS positions,
                   c.name AS club, l.name AS league, n.name AS nationality, r.accelerate AS accelerate,
                   v.potential AS potential, v.value_eur AS value_eur, (v.id IS NOT NULL) AS estimated
            """;
    private static final Map<String, String> SORTS = Map.of(
            "overall", "r.overall",
            "name", "b.full_name",
            "potential", "v.potential",
            "value", "v.value_eur");

    private final NamedParameterJdbcTemplate jdbc;

    public List<PlayerSummaryResponse> search(PlayerSearchRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(request, params);
        params.addValue("limit", request.getSize());
        params.addValue("offset", (long) request.getPage() * request.getSize());
        String sql = SELECT + FROM + where + " ORDER BY " + orderBy(request) + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, i) -> toSummary(rs));
    }

    public long count(PlayerSearchRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(request, params);
        Long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, params, Long.class);
        return Objects.isNull(total) ? 0 : total;
    }

    private String where(PlayerSearchRequest request, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("r.game_version_id = :gv");
        params.addValue("gv", request.getGameVersionId());
        if (Objects.nonNull(request.getQuery()) && !request.getQuery().isBlank()) {
            clauses.add("b.full_name ILIKE :q");
            params.addValue("q", "%" + request.getQuery().trim() + "%");
        }
        if (!request.getPositions().isEmpty()) {
            clauses.add("r.positions && string_to_array(:positions, ',')");
            params.addValue("positions", String.join(",", request.getPositions()));
        }
        if (!request.getAccelerate().isEmpty()) {
            clauses.add("r.accelerate IN (:accelerate)");
            params.addValue("accelerate", request.getAccelerate());
        }
        addPlaystyles(request, clauses, params);
        addAttrBounds(request.getAttrMin(), ">=", "min", clauses, params);
        addAttrBounds(request.getAttrMax(), "<=", "max", clauses, params);
        addBound(request.getOverallMin(), "r.overall >= :overallMin", "overallMin", clauses, params);
        addBound(request.getOverallMax(), "r.overall <= :overallMax", "overallMax", clauses, params);
        addBound(request.getPotentialMin(), "v.potential >= :potentialMin", "potentialMin", clauses, params);
        addBound(request.getValueMax(), "v.value_eur <= :valueMax", "valueMax", clauses, params);
        addBound(request.getClubId(), "r.club_id = :clubId", "clubId", clauses, params);
        addBound(request.getIdFilter(), "b.id = :playerId", "playerId", clauses, params);
        return " WHERE " + String.join(" AND ", clauses);
    }

    private void addPlaystyles(PlayerSearchRequest request, List<String> clauses, MapSqlParameterSource params) {
        for (int i = 0; i < request.getPlaystyles().size(); i++) {
            clauses.add("jsonb_exists(r.playstyles, :ps" + i + ")");
            params.addValue("ps" + i, request.getPlaystyles().get(i));
        }
    }

    private void addAttrBounds(Map<String, Integer> bounds, String operator, String prefix, List<String> clauses,
                               MapSqlParameterSource params) {
        int i = 0;
        for (Map.Entry<String, Integer> bound : bounds.entrySet()) {
            Attr attr = Attr.fromKey(bound.getKey()).orElseThrow(() -> new IllegalArgumentException(bound.getKey()));
            String name = prefix + "Attr" + i++;
            clauses.add("r." + attr.getColumn() + " " + operator + " :" + name);
            params.addValue(name, bound.getValue());
        }
    }

    private void addBound(Number value, String clause, String name, List<String> clauses, MapSqlParameterSource params) {
        if (Objects.nonNull(value)) {
            clauses.add(clause);
            params.addValue(name, value);
        }
    }

    private String orderBy(PlayerSearchRequest request) {
        String column = SORTS.get(Objects.isNull(request.getSort()) ? "overall" : request.getSort());
        if (Objects.isNull(column)) {
            column = Attr.fromKey(request.getSort().replace("_", "")).map(a -> "r." + a.getColumn()).orElse("r.overall");
        }
        return column + (request.isAscending() ? " ASC" : " DESC") + " NULLS LAST, b.id ASC";
    }

    private PlayerSummaryResponse toSummary(ResultSet rs) throws SQLException {
        Date dob = rs.getDate("dob");
        Array positions = rs.getArray("positions");
        return PlayerSummaryResponse.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .age(Objects.isNull(dob) ? null : Period.between(dob.toLocalDate(), LocalDate.now()).getYears())
                .overall(rs.getInt("overall"))
                .positions(Arrays.asList((String[]) positions.getArray()))
                .club(rs.getString("club"))
                .league(rs.getString("league"))
                .nationality(rs.getString("nationality"))
                .accelerate(rs.getString("accelerate"))
                .potential(nullableInt(rs, "potential"))
                .valueEur(nullableLong(rs, "value_eur"))
                .valuationEstimated(rs.getBoolean("estimated"))
                .build();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
