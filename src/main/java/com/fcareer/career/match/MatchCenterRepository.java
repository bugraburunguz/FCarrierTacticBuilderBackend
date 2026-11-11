package com.fcareer.career.match;

import com.fcareer.career.match.MatchCenterModels.CardRisk;
import com.fcareer.career.match.MatchCenterModels.DangerousPlayer;
import com.fcareer.career.match.MatchCenterModels.Opponent;
import com.fcareer.career.match.MatchCenterModels.PlayerRef;
import com.fcareer.career.match.MatchCenterModels.SquadMember;
import com.fcareer.career.match.MatchCenterModels.TeamMatchInfo;
import com.fcareer.career.match.MatchCenterModels.TeamRow;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@Repository
@RequiredArgsConstructor
public class MatchCenterRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void saveUserTeam(long careerId, int teamId) {
        jdbc.update("UPDATE career SET user_team_id = :t WHERE id = :c", Map.of("t", teamId, "c", careerId));
    }

    public void replaceTeamInfo(long careerId, List<TeamMatchInfo> rows) {
        jdbc.update("DELETE FROM career_team_match_info WHERE career_id = :c", Map.of("c", careerId));
        SqlParameterSource[] params = rows.stream().map((r) -> new MapSqlParameterSource().addValue("c", careerId).addValue("t", r.teamId())
                .addValue("name", r.formationName()).addValue("pos", toArrayLiteral(r.positions()), Types.VARCHAR).addValue("roles", toArrayLiteral(r.roles()), Types.VARCHAR)
                .addValue("captain", r.captainId(), Types.BIGINT).addValue("pen", r.penaltyTakerId(), Types.BIGINT).addValue("fk", r.freeKickTakerId(), Types.BIGINT)
                .addValue("lc", r.leftCornerTakerId(), Types.BIGINT).addValue("rc", r.rightCornerTakerId(), Types.BIGINT)
                .addValue("mo", r.matchdayOverall(), Types.SMALLINT).addValue("ma", r.matchdayAttack(), Types.SMALLINT)
                .addValue("mm", r.matchdayMidfield(), Types.SMALLINT).addValue("md", r.matchdayDefence(), Types.SMALLINT)).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO career_team_match_info (career_id, team_id, formation_name, formation_positions, formation_roles, captain_id, penalty_taker_id,
                  free_kick_taker_id, left_corner_taker_id, right_corner_taker_id, matchday_overall, matchday_attack, matchday_midfield, matchday_defence)
                VALUES (:c, :t, :name, CAST(:pos AS SMALLINT[]), CAST(:roles AS INTEGER[]), :captain, :pen, :fk, :lc, :rc, :mo, :ma, :mm, :md)
                ON CONFLICT (career_id, team_id) DO NOTHING""", params);
    }

    public Optional<Integer> findUserTeam(long careerId) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT user_team_id FROM career WHERE id = :c", Map.of("c", careerId), Integer.class));
    }

    public Optional<TeamRow> findTeam(long careerId, int teamId) {
        return jdbc.query("""
                SELECT team_id, name, league_name, overall, attack, midfield, defence, build_up_play, defensive_depth
                  FROM career_team WHERE career_id = :c AND team_id = :t""",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId), (rs, i) -> new TeamRow(rs.getInt("team_id"), rs.getString("name"),
                        rs.getString("league_name"), nullableInt(rs, "overall"), nullableInt(rs, "attack"), nullableInt(rs, "midfield"), nullableInt(rs, "defence"),
                        nullableInt(rs, "build_up_play"), nullableInt(rs, "defensive_depth"))).stream().findFirst();
    }

    public Optional<TeamMatchInfo> findMatchInfo(long careerId, int teamId) {
        return jdbc.query("SELECT * FROM career_team_match_info WHERE career_id = :c AND team_id = :t",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId), (rs, i) -> new TeamMatchInfo(rs.getInt("team_id"), rs.getString("formation_name"),
                        intArray(rs.getArray("formation_positions")), intArray(rs.getArray("formation_roles")), nullableLong(rs, "captain_id"),
                        nullableLong(rs, "penalty_taker_id"), nullableLong(rs, "free_kick_taker_id"), nullableLong(rs, "left_corner_taker_id"),
                        nullableLong(rs, "right_corner_taker_id"), nullableInt(rs, "matchday_overall"), nullableInt(rs, "matchday_attack"),
                        nullableInt(rs, "matchday_midfield"), nullableInt(rs, "matchday_defence"))).stream().findFirst();
    }

    public List<Opponent> findOpponents(long careerId, int userTeam) {
        return jdbc.query("""
                WITH ours AS (SELECT league_name FROM career_team WHERE career_id = :c AND team_id = :u),
                     shared AS (SELECT DISTINCT s.team_id, s.competition_id FROM career_player_season_stat s
                                 WHERE s.career_id = :c AND s.competition_id IN (SELECT competition_id FROM career_player_season_stat
                                                                                 WHERE career_id = :c AND team_id = :u))
                SELECT t.team_id, t.name, t.league_name, t.overall, (t.league_name = (SELECT league_name FROM ours)) AS same_league,
                       array_remove(array_agg(DISTINCT sh.competition_id), NULL) AS competitions
                  FROM career_team t
                  LEFT JOIN shared sh ON sh.team_id = t.team_id
                 WHERE t.career_id = :c AND t.team_id <> :u AND NOT t.free_agent_pool
                   AND (t.league_name = (SELECT league_name FROM ours) OR sh.team_id IS NOT NULL)
                 GROUP BY t.team_id, t.name, t.league_name, t.overall
                 ORDER BY (t.league_name = (SELECT league_name FROM ours)) DESC, t.overall DESC NULLS LAST, t.name""",
                new MapSqlParameterSource("c", careerId).addValue("u", userTeam),
                (rs, i) -> new Opponent(rs.getInt("team_id"), rs.getString("name"), rs.getString("league_name"), nullableInt(rs, "overall"),
                        rs.getBoolean("same_league"), intArray(rs.getArray("competitions"))));
    }

    public List<SquadMember> findSquad(long careerId, int teamId, int minAppearances) {
        return jdbc.query("""
                SELECT cp.player_id, CASE WHEN sum(s.appearances) >= :minApps
                         THEN round(sum(s.rating_total)::numeric / sum(s.appearances) / 10, 1) END AS avg_rating
                  FROM career_player cp
                  LEFT JOIN career_player_season_stat s ON s.career_id = cp.career_id AND s.player_id = cp.player_id AND s.team_id = cp.team_id
                 WHERE cp.career_id = :c AND cp.team_id = :t
                 GROUP BY cp.player_id, cp.ovr
                 ORDER BY cp.ovr DESC, cp.player_id""",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId).addValue("minApps", minAppearances),
                (rs, i) -> new SquadMember(rs.getLong("player_id"), nullableDouble(rs, "avg_rating")));
    }

    public List<DangerousPlayer> findDangerousPlayers(long careerId, int teamId, int limit) {
        return jdbc.query("""
                SELECT cp.player_id, b.full_name, cp.ovr, COALESCE(sum(s.appearances), 0) AS apps, COALESCE(sum(s.goals), 0) AS goals,
                       COALESCE(sum(s.assists), 0) AS assists, round(sum(s.rating_total)::numeric / NULLIF(sum(s.appearances), 0) / 10, 1) AS avg_rating
                  FROM career_player cp
                  JOIN base_player b ON b.id = cp.player_id
                  LEFT JOIN career_player_season_stat s ON s.career_id = cp.career_id AND s.player_id = cp.player_id AND s.team_id = cp.team_id
                 WHERE cp.career_id = :c AND cp.team_id = :t
                 GROUP BY cp.player_id, b.full_name, cp.ovr
                HAVING COALESCE(sum(s.goals), 0) + COALESCE(sum(s.assists), 0) > 0
                 ORDER BY COALESCE(sum(s.goals), 0) + COALESCE(sum(s.assists), 0) DESC, avg_rating DESC NULLS LAST, cp.player_id
                 LIMIT :limit""",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId).addValue("limit", limit),
                (rs, i) -> new DangerousPlayer(rs.getLong("player_id"), rs.getString("full_name"), nullableInt(rs, "ovr"), rs.getInt("apps"), rs.getInt("goals"), rs.getInt("assists"), nullableDouble(rs, "avg_rating")));
    }

    public List<CardRisk> findCardRisk(long careerId, int teamId, int limit) {
        return jdbc.query("""
                SELECT s.player_id, b.full_name, sum(s.appearances) AS apps, sum(s.yellow_cards) AS yellow, sum(s.red_cards) AS red
                  FROM career_player_season_stat s
                  JOIN career_player cp ON cp.career_id = s.career_id AND cp.player_id = s.player_id AND cp.team_id = s.team_id
                  JOIN base_player b ON b.id = s.player_id
                 WHERE s.career_id = :c AND s.team_id = :t
                 GROUP BY s.player_id, b.full_name
                HAVING sum(s.yellow_cards) + sum(s.red_cards) > 0
                 ORDER BY sum(s.red_cards) DESC, sum(s.yellow_cards) DESC, sum(s.appearances) ASC, s.player_id
                 LIMIT :limit""",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId).addValue("limit", limit),
                (rs, i) -> new CardRisk(rs.getLong("player_id"), rs.getString("full_name"), rs.getInt("apps"), rs.getInt("yellow"), rs.getInt("red")));
    }

    public boolean hasSeasonStats(long careerId, int teamId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM career_player_season_stat WHERE career_id = :c AND team_id = :t)",
                new MapSqlParameterSource("c", careerId).addValue("t", teamId), Boolean.class));
    }

    public Map<Long, PlayerRef> findNames(Collection<Long> playerIds) {
        List<Long> ids = playerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PlayerRef> out = new java.util.HashMap<>();
        jdbc.query("SELECT id, full_name FROM base_player WHERE id IN (:ids)", Map.of("ids", ids),
                (rs) -> {
                    out.put(rs.getLong("id"), new PlayerRef(rs.getLong("id"), rs.getString("full_name")));
                });
        return out;
    }

    private static String toArrayLiteral(List<Integer> values) {
        if (Objects.isNull(values)) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        values.forEach((v) -> joiner.add(Objects.isNull(v) ? "NULL" : String.valueOf(v)));
        return joiner.toString();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static List<Integer> intArray(Array array) throws SQLException {
        if (Objects.isNull(array)) {
            return null;
        }
        Object[] values = (Object[]) array.getArray();
        List<Integer> out = new ArrayList<>(values.length);
        Arrays.stream(values).forEach((v) -> out.add(Objects.isNull(v) ? null : ((Number) v).intValue()));
        return out;
    }
}
