package com.fcareer.manager;

import com.fcareer.manager.ManagerModels.Background;
import com.fcareer.manager.ManagerModels.Difficulty;
import com.fcareer.manager.ManagerModels.Licence;
import com.fcareer.manager.ManagerModels.ManagerProfile;
import com.fcareer.manager.ManagerModels.Priority;
import com.fcareer.manager.ManagerModels.Style;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MGR-a: menajer profili (kullanıcı başına bir satır) ve katalog kulüp istatistikleri (club_profile + katalog kadrosu, kariyer verisi yok). */
@Repository
@RequiredArgsConstructor
public class ManagerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Kulüp istatistiği: prestijler, lig seviyesi, altyapı gelişimi; katalog kadrosundan (üretilmiş oyuncular hariç) oyuncu sayısı, en iyi
     * strengthTop oyuncunun ortalama OVR'si, en iyi budgetTop oyuncunun MODELED değer toplamı ve genç yetenek sayısı (≤ youngMaxAge, POT − OVR ≥ headroom).
     */
    public record ClubStat(long clubId, String name, Integer leagueId, String league, int domestic, int international, int leagueLevel, int youthDevelopment,
                           Long clubWorthEur, int players, double avgOvr, long topValueEur, int youngCount) {
    }

    public Optional<ManagerProfile> profile(long userId) {
        return jdbc.query("SELECT * FROM manager_profile WHERE user_id = :u", Map.of("u", userId), (rs, i) -> new ManagerProfile(rs.getString("name"),
                Licence.valueOf(rs.getString("licence")), Background.valueOf(rs.getString("background")), rs.getString("formation"),
                Style.valueOf(rs.getString("style")), priorities(rs.getArray("priorities")), Difficulty.valueOf(rs.getString("difficulty")),
                rs.getObject("league_id", Integer.class), rs.getObject("gender", Integer.class))).stream().findFirst();
    }

    public void saveProfile(long userId, ManagerProfile p) {
        jdbc.update("""
                INSERT INTO manager_profile (user_id, name, licence, background, formation, style, priorities, difficulty, league_id, gender, updated_at)
                VALUES (:u, :name, :licence, :background, :formation, :style, CAST(:priorities AS text[]), :difficulty, :league, :gender, now())
                ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, licence = EXCLUDED.licence, background = EXCLUDED.background,
                  formation = EXCLUDED.formation, style = EXCLUDED.style, priorities = EXCLUDED.priorities, difficulty = EXCLUDED.difficulty,
                  league_id = EXCLUDED.league_id, gender = EXCLUDED.gender, updated_at = now()""",
                new MapSqlParameterSource().addValue("u", userId).addValue("name", p.name()).addValue("licence", p.licence().name())
                        .addValue("background", p.background().name()).addValue("formation", p.formation()).addValue("style", p.style().name())
                        .addValue("priorities", "{" + String.join(",", p.priorities().stream().map(Enum::name).toList()) + "}")
                        .addValue("difficulty", p.difficulty().name()).addValue("league", p.leagueId(), java.sql.Types.INTEGER)
                        .addValue("gender", p.gender(), java.sql.Types.SMALLINT));
    }

    public boolean leagueExists(int leagueId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM league WHERE id = :l)", Map.of("l", leagueId), Boolean.class));
    }

    /** Katalog sürümü (önbellek anahtarı): kulüp profillerinin son güncellemesi ve sayısı. */
    public String catalogVersion(short gameVersionId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(to_char(max(updated_at), 'YYYYMMDDHH24MISSUS'), '0') || '-' || count(*) FROM club_profile WHERE game_version_id = :gv""",
                Map.of("gv", gameVersionId), String.class);
    }

    public int profileCount(short gameVersionId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM club_profile WHERE game_version_id = :gv", Map.of("gv", gameVersionId), Integer.class);
        return n == null ? 0 : n;
    }

    public List<ClubStat> clubStats(short gameVersionId, int gender, ManagerProperties p) {
        return jdbc.query("""
                WITH squad AS (
                  SELECT r.club_id, r.overall, b.dob, pv.potential, pv.value_eur,
                         row_number() OVER (PARTITION BY r.club_id ORDER BY r.overall DESC, r.base_player_id) AS rn
                    FROM player_rating r
                    JOIN base_player b ON b.id = r.base_player_id AND b.owner_career_id IS NULL
                    LEFT JOIN player_valuation pv ON pv.base_player_id = r.base_player_id AND pv.game_version_id = r.game_version_id
                         AND pv.source = 'MODELED' AND pv.owner_user_id IS NULL
                   WHERE r.game_version_id = :gv AND r.club_id IN (SELECT club_id FROM club_profile WHERE game_version_id = :gv))
                SELECT p.club_id, c.name, c.league_id, l.name AS league, p.domestic_prestige, p.international_prestige, p.league_level, p.youth_development,
                       p.club_worth_eur, count(s.club_id) AS players,
                       avg(s.overall) FILTER (WHERE s.rn <= :strengthTop) AS avg_ovr,
                       COALESCE(sum(s.value_eur) FILTER (WHERE s.rn <= :budgetTop), 0) AS top_value,
                       count(*) FILTER (WHERE s.dob IS NOT NULL AND date_part('year', age(CURRENT_DATE, s.dob)) <= :youngAge
                                        AND s.potential - s.overall >= :headroom) AS young
                  FROM club_profile p
                  JOIN club c ON c.id = p.club_id
                  LEFT JOIN league l ON l.id = c.league_id
                  LEFT JOIN squad s ON s.club_id = p.club_id
                 WHERE p.game_version_id = :gv AND COALESCE(c.gender, 0) = :gender
                 GROUP BY p.club_id, c.name, c.league_id, l.name, p.domestic_prestige, p.international_prestige, p.league_level, p.youth_development, p.club_worth_eur
                HAVING count(s.club_id) >= 11""",
                new MapSqlParameterSource().addValue("gv", gameVersionId).addValue("gender", gender).addValue("strengthTop", p.getStrengthTop())
                        .addValue("budgetTop", p.getBudgetTop()).addValue("youngAge", p.getYoungMaxAge()).addValue("headroom", p.getYoungMinHeadroom()),
                (rs, i) -> new ClubStat(rs.getLong("club_id"), rs.getString("name"), rs.getObject("league_id", Integer.class), rs.getString("league"),
                        rs.getInt("domestic_prestige"), rs.getInt("international_prestige"), Math.max(1, rs.getInt("league_level")), rs.getInt("youth_development"),
                        rs.getObject("club_worth_eur", Long.class), rs.getInt("players"), rs.getDouble("avg_ovr"), rs.getLong("top_value"), rs.getInt("young")));
    }

    private static List<Priority> priorities(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((String[]) array.getArray()).map(Priority::valueOf).toList();
    }
}
