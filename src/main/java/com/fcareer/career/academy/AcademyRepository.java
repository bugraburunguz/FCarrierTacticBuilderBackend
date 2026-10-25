package com.fcareer.career.academy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ACADEMY-a: kariyerin altyapı takımı (career.academy_team_id) ve o takımdaki içe aktarılmış ("live") oyuncular. Yalnız verilen kariyerin satırları
 * okunur; sahiplik AcademyService'te doğrulanır. Yaş, kariyerin oyun tarihine göre (yoksa bugün).
 */
@Repository
@RequiredArgsConstructor
public class AcademyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Team(Integer teamId, String name) {
    }

    public record Row(long playerId, String name, Integer age, List<String> positions, Integer overall, Integer potential,
                      Integer monthsInSquad, Integer playerTier, Integer potentialVariance, Integer swingLowPotential) {
    }

    public Optional<Team> team(long careerId) {
        return jdbc.query("""
                SELECT k.academy_team_id, t.name
                  FROM career k
                  LEFT JOIN career_team t ON t.career_id = k.id AND t.team_id = k.academy_team_id
                 WHERE k.id = :c AND k.academy_team_id IS NOT NULL""", Map.of("c", careerId),
                (rs, i) -> new Team(rs.getObject("academy_team_id", Integer.class), rs.getString("name"))).stream().findFirst();
    }

    public List<Row> players(long careerId, int teamId) {
        return jdbc.query("""
                SELECT cp.player_id, b.full_name, cp.positions, cp.ovr, cp.pot,
                       CASE WHEN COALESCE(cp.dob, b.dob) IS NULL THEN NULL
                            ELSE date_part('year', age(COALESCE(k.current_game_date, CURRENT_DATE), COALESCE(cp.dob, b.dob)))::int END AS age,
                       y.months_in_squad, y.player_tier, y.potential_variance, y.swing_low_potential
                  FROM career_player cp
                  JOIN career k ON k.id = cp.career_id
                  JOIN base_player b ON b.id = cp.player_id
                  LEFT JOIN career_youth_player y ON y.career_id = cp.career_id AND y.base_player_id = cp.player_id
                 WHERE cp.career_id = :c AND cp.live AND cp.team_id = :t
                 ORDER BY cp.pot DESC NULLS LAST, cp.ovr DESC, b.full_name""", Map.of("c", careerId, "t", teamId), (rs, i) -> new Row(
                rs.getLong("player_id"), rs.getString("full_name"), rs.getObject("age", Integer.class),
                Arrays.asList((String[]) rs.getArray("positions").getArray()), rs.getObject("ovr", Integer.class), rs.getObject("pot", Integer.class),
                rs.getObject("months_in_squad", Integer.class), rs.getObject("player_tier", Integer.class),
                rs.getObject("potential_variance", Integer.class), rs.getObject("swing_low_potential", Integer.class)));
    }
}
