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
            SELECT b.id AS id, b.full_name AS name, b.dob AS dob, b.gender AS gender, b.preferred_foot AS foot,
                   r.overall AS overall, r.positions AS positions, r.weak_foot AS weak_foot, r.skill_moves AS skill_moves,
                   c.id AS club_id, c.name AS club, l.name AS league, n.name AS nationality, r.accelerate AS accelerate, r.run_style1 AS run_style,
                   v.potential AS potential, v.value_eur AS value_eur, (v.id IS NOT NULL) AS estimated
            """;
    private static final String AGE_YEARS = "EXTRACT(YEAR FROM age(b.dob))";
    private static final String AGE_SORT = "age";
    private static final Map<String, String> SORTS = Map.ofEntries(
            Map.entry("overall", "r.overall"),
            Map.entry("name", "b.full_name"),
            Map.entry("potential", "v.potential"),
            Map.entry("value", "v.value_eur"),
            Map.entry("club", "c.name"),
            Map.entry("league", "l.name"),
            Map.entry("nationality", "n.name"),
            Map.entry("accelerate", "r.accelerate"),
            Map.entry("position", "r.positions[1]"),
            Map.entry("weakfoot", "r.weak_foot"),
            Map.entry("skillmoves", "r.skill_moves"));

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
        addBound(request.getPotentialMax(), "v.potential <= :potentialMax", "potentialMax", clauses, params);
        addBound(request.getValueMax(), request.isIncludeUnvalued()
                ? "(v.value_eur IS NULL OR v.value_eur <= :valueMax)" : "v.value_eur <= :valueMax", "valueMax", clauses, params);
        addBound(request.getClubId(), "r.club_id = :clubId", "clubId", clauses, params);
        addBound(request.getLeagueId(), "c.league_id = :leagueId", "leagueId", clauses, params);
        addBound(request.getNationalityId(), "b.nationality_id = :nationalityId", "nationalityId", clauses, params);
        addBound(request.getGender(), "b.gender = :gender", "gender", clauses, params);
        addBound(request.getAgeMin(), AGE_YEARS + " >= :ageMin", "ageMin", clauses, params);
        addBound(request.getAgeMax(), AGE_YEARS + " <= :ageMax", "ageMax", clauses, params);
        addBound(request.getWeakFootMin(), "r.weak_foot >= :weakFootMin", "weakFootMin", clauses, params);
        addBound(request.getSkillMovesMin(), "r.skill_moves >= :skillMovesMin", "skillMovesMin", clauses, params);
        if (Objects.nonNull(request.getFoot()) && !request.getFoot().isBlank()) {
            clauses.add("b.preferred_foot = :foot");
            params.addValue("foot", request.getFoot());
        }
        addBound(request.getIdFilter(), "b.id = :playerId", "playerId", clauses, params);
        addIds(request.getIds(), "b.id IN (:ids)", "ids", clauses, params);
        addIds(request.getExcludeIds(), "b.id NOT IN (:excludeIds)", "excludeIds", clauses, params);
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

    private void addIds(List<Long> ids, String clause, String name, List<String> clauses, MapSqlParameterSource params) {
        if (Objects.nonNull(ids) && !ids.isEmpty()) {
            clauses.add(clause);
            params.addValue(name, ids);
        }
    }

    private void addBound(Number value, String clause, String name, List<String> clauses, MapSqlParameterSource params) {
        if (Objects.nonNull(value)) {
            clauses.add(clause);
            params.addValue(name, value);
        }
    }

    private String orderBy(PlayerSearchRequest request) {
        String key = Objects.isNull(request.getSort()) ? "overall" : request.getSort().replace("_", "").toLowerCase();
        boolean ascending = request.isAscending();
        String column = SORTS.get(key);
        if (AGE_SORT.equals(key)) {
            // Genç → yaşlı sıralaması doğum tarihinin ters yönüdür.
            column = "b.dob";
            ascending = !ascending;
        }
        if (Objects.isNull(column)) {
            column = Attr.fromKey(key).map(a -> "r." + a.getColumn()).orElse("r.overall");
        }
        return column + (ascending ? " ASC" : " DESC") + " NULLS LAST, b.id ASC";
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
                .clubId(nullableInt(rs, "club_id"))
                .club(rs.getString("club"))
                .league(rs.getString("league"))
                .nationality(rs.getString("nationality"))
                .gender(nullableInt(rs, "gender"))
                .preferredFoot(rs.getString("foot"))
                .weakFoot(nullableInt(rs, "weak_foot"))
                .skillMoves(nullableInt(rs, "skill_moves"))
                .accelerate(rs.getString("accelerate"))
                .runStyle(nullableInt(rs, "run_style"))
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
