package com.fcareer.career.match;

import com.fcareer.career.match.MatchModels.CompetitionSummary;
import com.fcareer.career.match.MatchModels.Leader;
import com.fcareer.career.match.MatchModels.MatchRating;
import com.fcareer.career.match.MatchModels.Metric;
import com.fcareer.career.match.MatchModels.RatingRow;
import com.fcareer.career.match.MatchModels.SeasonLine;
import com.fcareer.career.match.MatchModels.SeasonRow;
import com.fcareer.career.match.MatchModels.SquadForm;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class MatchDataRepository {

    private static final String AVERAGE = "round(sum(s.rating_total)::numeric / NULLIF(sum(s.appearances), 0) / 10, 1)";
    private static final Map<Metric, String> LEADER_ORDER = Map.of(
            Metric.GOALS, "goals DESC, assists DESC, appearances ASC",
            Metric.ASSISTS, "assists DESC, goals DESC, appearances ASC",
            Metric.RATING, "average_rating DESC NULLS LAST, appearances DESC");

    private final NamedParameterJdbcTemplate jdbc;

    public int upsertRatings(long careerId, List<RatingRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] params = rows.stream().map((r) -> new MapSqlParameterSource().addValue("c", careerId).addValue("p", r.playerId())
                .addValue("d", java.sql.Date.valueOf(r.date())).addValue("m", r.minutes(), Types.SMALLINT).addValue("pos", r.position(), Types.SMALLINT)
                .addValue("r", r.rating())).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO career_player_match_rating (career_id, player_id, match_date, minutes, position_code, rating) VALUES (:c, :p, :d, :m, :pos, :r)
                ON CONFLICT (career_id, player_id, match_date) DO UPDATE SET minutes = EXCLUDED.minutes, position_code = EXCLUDED.position_code, rating = EXCLUDED.rating""",
                params);
        return rows.size();
    }

    public int replaceSeasonStats(long careerId, List<SeasonRow> rows) {
        jdbc.update("DELETE FROM career_player_season_stat WHERE career_id = :c", Map.of("c", careerId));
        SqlParameterSource[] params = rows.stream().map((r) -> new MapSqlParameterSource().addValue("c", careerId).addValue("p", r.playerId())
                .addValue("t", r.teamId()).addValue("comp", r.competitionId()).addValue("app", r.appearances()).addValue("g", r.goals())
                .addValue("a", r.assists()).addValue("total", r.ratingTotal()).addValue("y", r.yellowCards()).addValue("red", r.redCards())
                .addValue("cs", r.cleanSheets())).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO career_player_season_stat (career_id, player_id, team_id, competition_id, appearances, goals, assists, rating_total,
                  yellow_cards, red_cards, clean_sheets)
                VALUES (:c, :p, :t, :comp, :app, :g, :a, :total, :y, :red, :cs)
                ON CONFLICT (career_id, player_id, team_id, competition_id) DO UPDATE SET appearances = EXCLUDED.appearances, goals = EXCLUDED.goals,
                  assists = EXCLUDED.assists, rating_total = EXCLUDED.rating_total, yellow_cards = EXCLUDED.yellow_cards,
                  red_cards = EXCLUDED.red_cards, clean_sheets = EXCLUDED.clean_sheets""", params);
        return rows.size();
    }

    public List<MatchRating> ratings(long careerId, long playerId) {
        return jdbc.query("""
                SELECT match_date, minutes, position_code, rating FROM career_player_match_rating
                 WHERE career_id = :c AND player_id = :p ORDER BY match_date DESC""",
                new MapSqlParameterSource("c", careerId).addValue("p", playerId),
                (rs, i) -> new MatchRating(rs.getDate("match_date").toLocalDate(), nullableInt(rs, "minutes"), nullableInt(rs, "position_code"), rs.getInt("rating")));
    }

    public List<SeasonLine> seasons(long careerId, long playerId) {
        return jdbc.query("""
                SELECT s.team_id, t.name AS team_name, s.competition_id, s.appearances, s.goals, s.assists,
                       round(s.rating_total::numeric / NULLIF(s.appearances, 0) / 10, 1) AS average_rating, s.yellow_cards, s.red_cards, s.clean_sheets
                  FROM career_player_season_stat s
                  LEFT JOIN career_team t ON t.career_id = s.career_id AND t.team_id = s.team_id
                 WHERE s.career_id = :c AND s.player_id = :p
                 ORDER BY s.appearances DESC, s.competition_id""",
                new MapSqlParameterSource("c", careerId).addValue("p", playerId),
                (rs, i) -> new SeasonLine(rs.getInt("team_id"), rs.getString("team_name"), rs.getInt("competition_id"), rs.getInt("appearances"),
                        rs.getInt("goals"), rs.getInt("assists"), nullableDouble(rs, "average_rating"), rs.getInt("yellow_cards"), rs.getInt("red_cards"),
                        rs.getInt("clean_sheets")));
    }

    public List<SquadForm> squadForm(long careerId, List<Long> playerIds, int recent) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT b.id AS player_id, b.full_name,
                       (SELECT array_agg(r.rating ORDER BY r.match_date DESC) FROM (
                          SELECT rating, match_date FROM career_player_match_rating
                           WHERE career_id = :c AND player_id = b.id ORDER BY match_date DESC LIMIT :recent) r) AS last_ratings,
                       COALESCE(sum(s.appearances), 0) AS appearances, COALESCE(sum(s.goals), 0) AS goals, COALESCE(sum(s.assists), 0) AS assists,
                       """ + AVERAGE + """
                 AS average_rating
                  FROM base_player b
                  LEFT JOIN career_player_season_stat s ON s.career_id = :c AND s.player_id = b.id
                 WHERE b.id IN (:ids)
                 GROUP BY b.id, b.full_name
                 ORDER BY b.full_name""",
                new MapSqlParameterSource("c", careerId).addValue("ids", playerIds).addValue("recent", recent),
                (rs, i) -> new SquadForm(rs.getLong("player_id"), rs.getString("full_name"), intArray(rs.getArray("last_ratings")), rs.getInt("appearances"),
                        rs.getInt("goals"), rs.getInt("assists"), nullableDouble(rs, "average_rating")));
    }

    public List<CompetitionSummary> competitions(long careerId) {
        return jdbc.query("""
                SELECT competition_id, count(DISTINCT team_id) AS teams, count(DISTINCT player_id) FILTER (WHERE appearances > 0) AS players,
                       sum(goals) AS goals
                  FROM career_player_season_stat WHERE career_id = :c
                 GROUP BY competition_id ORDER BY count(DISTINCT team_id) DESC, competition_id""",
                Map.of("c", careerId),
                (rs, i) -> new CompetitionSummary(rs.getInt("competition_id"), rs.getInt("teams"), rs.getInt("players"), rs.getInt("goals")));
    }

    public List<Leader> leaders(long careerId, Integer competitionId, Metric metric, int limit, int minAppearances) {
        return jdbc.query("""
                SELECT s.player_id, b.full_name, s.team_id, t.name AS team_name, sum(s.appearances) AS appearances, sum(s.goals) AS goals,
                       sum(s.assists) AS assists,\s""" + AVERAGE + """
                 AS average_rating
                  FROM career_player_season_stat s
                  JOIN base_player b ON b.id = s.player_id
                  LEFT JOIN career_team t ON t.career_id = s.career_id AND t.team_id = s.team_id
                 WHERE s.career_id = :c AND (CAST(:comp AS INTEGER) IS NULL OR s.competition_id = :comp)
                 GROUP BY s.player_id, b.full_name, s.team_id, t.name
                HAVING sum(s.appearances) >= :minApps
                 ORDER BY\s""" + LEADER_ORDER.get(metric) + """
                , s.player_id LIMIT :limit""",
                new MapSqlParameterSource("c", careerId).addValue("comp", competitionId, Types.INTEGER).addValue("limit", limit)
                        .addValue("minApps", metric == Metric.RATING ? minAppearances : 1),
                (rs, i) -> new Leader(rs.getLong("player_id"), rs.getString("full_name"), rs.getInt("team_id"), rs.getString("team_name"),
                        rs.getInt("appearances"), rs.getInt("goals"), rs.getInt("assists"), nullableDouble(rs, "average_rating")));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static List<Integer> intArray(Array array) throws SQLException {
        if (Objects.isNull(array)) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<Integer> out = new ArrayList<>(values.length);
        Arrays.stream(values).forEach((v) -> out.add(((Number) v).intValue()));
        return out;
    }
}
