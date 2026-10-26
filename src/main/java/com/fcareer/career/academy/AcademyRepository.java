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

    /** dob ve gameDate (GROWTH-CAL): 16 yaş terfi tarihi ve ufku için. */
    public record Row(long playerId, String name, Integer age, List<String> positions, Integer overall, Integer potential,
                      Integer monthsInSquad, Integer playerTier, Integer potentialVariance, Integer swingLowPotential,
                      java.time.LocalDate dob, java.time.LocalDate gameDate) {
    }

    public Optional<Team> team(long careerId) {
        return jdbc.query("""
                SELECT k.academy_team_id, t.name
                  FROM career k
                  LEFT JOIN career_team t ON t.career_id = k.id AND t.team_id = k.academy_team_id
                 WHERE k.id = :c AND k.academy_team_id IS NOT NULL""", Map.of("c", careerId),
                (rs, i) -> new Team(rs.getObject("academy_team_id", Integer.class), rs.getString("name"))).stream().findFirst();
    }

    private static final String ROW_SELECT = """
                SELECT cp.player_id, b.full_name, cp.positions, cp.ovr, cp.pot,
                       CASE WHEN COALESCE(cp.dob, b.dob) IS NULL THEN NULL
                            ELSE date_part('year', age(COALESCE(k.current_game_date, CURRENT_DATE), COALESCE(cp.dob, b.dob)))::int END AS age,
                       y.months_in_squad, y.player_tier, y.potential_variance, y.swing_low_potential,
                       COALESCE(cp.dob, b.dob) AS dob, k.current_game_date AS game_date
                  FROM career_player cp
                  JOIN career k ON k.id = cp.career_id
                  JOIN base_player b ON b.id = cp.player_id
                  LEFT JOIN career_youth_player y ON y.career_id = cp.career_id AND y.base_player_id = cp.player_id
                """;
    // Potansiyele göre sıralanmaz: aralık modunda yanıt sırası bant içi gerçek POT sırasını vermesin (sıralama istemcide, bantla).
    private static final String ROW_ORDER = " ORDER BY cp.ovr DESC, b.full_name, cp.player_id";

    public List<Row> players(long careerId, int teamId) {
        return jdbc.query(ROW_SELECT + " WHERE cp.career_id = :c AND cp.live AND cp.team_id = :t" + ROW_ORDER, Map.of("c", careerId, "t", teamId), this::row);
    }

    /** ACADEMY-b: kadro gençleri (Gelişim) ve panel için aynı satır, kimlikle (yalnız bu kariyerin kaydı). */
    public List<Row> playersByIds(long careerId, java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(ROW_SELECT + " WHERE cp.career_id = :c AND cp.player_id IN (:ids)" + ROW_ORDER, Map.of("c", careerId, "ids", ids), this::row);
    }

    /** Oyuncu başına son içe aktarmalardaki OVR, yeniden eskiye (en çok `limit`); anlık görüntüler kariyerin canlı oyuncularını (altyapı dahil) tutar. */
    public Map<Long, List<Integer>> recentOveralls(long careerId, java.util.Collection<Long> ids, int limit) {
        Map<Long, List<Integer>> out = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT player_id, overall FROM (
                  SELECT sp.base_player_id AS player_id, sp.overall,
                         row_number() OVER (PARTITION BY sp.base_player_id ORDER BY s.taken_at DESC, s.id DESC) AS n
                    FROM career_snapshot_player sp JOIN career_snapshot s ON s.id = sp.snapshot_id
                   WHERE s.career_id = :c AND s.source = 'IMPORT' AND sp.base_player_id IN (:ids)) x
                 WHERE n <= :limit ORDER BY player_id, n""", Map.of("c", careerId, "ids", ids, "limit", limit),
                (org.springframework.jdbc.core.RowCallbackHandler) (rs) -> out.computeIfAbsent(rs.getLong("player_id"), (k) -> new java.util.ArrayList<>())
                        .add(rs.getObject("overall", Integer.class)));
        return out;
    }

    private Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Row(
                rs.getLong("player_id"), rs.getString("full_name"), rs.getObject("age", Integer.class),
                Arrays.asList((String[]) rs.getArray("positions").getArray()), rs.getObject("ovr", Integer.class), rs.getObject("pot", Integer.class),
                rs.getObject("months_in_squad", Integer.class), rs.getObject("player_tier", Integer.class),
                rs.getObject("potential_variance", Integer.class), rs.getObject("swing_low_potential", Integer.class),
                rs.getObject("dob", java.time.LocalDate.class), rs.getObject("game_date", java.time.LocalDate.class));
    }
}
