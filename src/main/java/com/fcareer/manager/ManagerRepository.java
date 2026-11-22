package com.fcareer.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.fitengine.model.Attr;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** MGR-a: menajer profili (kullanıcı başına bir satır) ve katalog kulüp istatistikleri (club_profile + katalog kadrosu, kariyer verisi yok). */
@Repository
@RequiredArgsConstructor
public class ManagerRepository {

    private static final String RATINGS_TOKEN = "{ratings}";
    private static final String PROFILES_TOKEN = "{profiles}";
    private static final String ARCHIVED_CATALOG_PREFIX = "sv-";
    private static final String ARCHIVED_RATINGS = "(SELECT base_player_id, game_version_id, club_id, overall FROM player_rating_archive WHERE squad_version_id = :sv)";
    private static final String ARCHIVED_PROFILES = "(SELECT * FROM club_profile_archive WHERE squad_version_id = :sv)";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RatingAttrAccessor ratingAttrAccessor;

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
        return catalogVersion(gameVersionId, null);
    }

    public String catalogVersion(short gameVersionId, Long archivedSquadVersionId) {
        if (Objects.nonNull(archivedSquadVersionId)) {
            return ARCHIVED_CATALOG_PREFIX + archivedSquadVersionId;
        }
        return jdbc.queryForObject("""
                SELECT COALESCE(to_char(max(updated_at), 'YYYYMMDDHH24MISSUS'), '0') || '-' || count(*) FROM club_profile WHERE game_version_id = :gv""",
                Map.of("gv", gameVersionId), String.class);
    }

    public int profileCount(short gameVersionId) {
        return profileCount(gameVersionId, null);
    }

    public int profileCount(short gameVersionId, Long archivedSquadVersionId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + profiles(archivedSquadVersionId) + " cp WHERE cp.game_version_id = :gv",
                withSquadVersion(new MapSqlParameterSource("gv", gameVersionId), archivedSquadVersionId), Integer.class);
        return n == null ? 0 : n;
    }

    public List<ClubStat> clubStats(short gameVersionId, int gender, ManagerProperties p) {
        return clubStats(gameVersionId, gender, p, null);
    }

    public List<ClubStat> clubStats(short gameVersionId, int gender, ManagerProperties p, Long archivedSquadVersionId) {
        return jdbc.query("""
                WITH squad AS (
                  SELECT r.club_id, r.overall, b.dob, pv.potential, pv.value_eur,
                         row_number() OVER (PARTITION BY r.club_id ORDER BY r.overall DESC, r.base_player_id) AS rn
                    FROM {ratings} r
                    JOIN base_player b ON b.id = r.base_player_id AND b.owner_career_id IS NULL
                    LEFT JOIN player_valuation pv ON pv.base_player_id = r.base_player_id AND pv.game_version_id = r.game_version_id
                         AND pv.source = 'MODELED' AND pv.owner_user_id IS NULL
                   WHERE r.game_version_id = :gv AND r.club_id IN (SELECT cp.club_id FROM {profiles} cp WHERE cp.game_version_id = :gv))
                SELECT p.club_id, c.name, c.league_id, l.name AS league, p.domestic_prestige, p.international_prestige, p.league_level, p.youth_development,
                       p.club_worth_eur, count(s.club_id) AS players,
                       avg(s.overall) FILTER (WHERE s.rn <= :strengthTop) AS avg_ovr,
                       COALESCE(sum(s.value_eur) FILTER (WHERE s.rn <= :budgetTop), 0) AS top_value,
                       count(*) FILTER (WHERE s.dob IS NOT NULL AND date_part('year', age(CURRENT_DATE, s.dob)) <= :youngAge
                                        AND s.potential - s.overall >= :headroom) AS young
                  FROM {profiles} p
                  JOIN club c ON c.id = p.club_id
                  LEFT JOIN league l ON l.id = c.league_id
                  LEFT JOIN squad s ON s.club_id = p.club_id
                 WHERE p.game_version_id = :gv AND COALESCE(c.gender, 0) = :gender
                 GROUP BY p.club_id, c.name, c.league_id, l.name, p.domestic_prestige, p.international_prestige, p.league_level, p.youth_development, p.club_worth_eur
                HAVING count(s.club_id) >= 11""".replace(RATINGS_TOKEN, ratings(archivedSquadVersionId)).replace(PROFILES_TOKEN, profiles(archivedSquadVersionId)),
                withSquadVersion(new MapSqlParameterSource().addValue("gv", gameVersionId).addValue("gender", gender).addValue("strengthTop", p.getStrengthTop())
                        .addValue("budgetTop", p.getBudgetTop()).addValue("youngAge", p.getYoungMaxAge()).addValue("headroom", p.getYoungMinHeadroom()),
                        archivedSquadVersionId),
                (rs, i) -> new ClubStat(rs.getLong("club_id"), rs.getString("name"), rs.getObject("league_id", Integer.class), rs.getString("league"),
                        rs.getInt("domestic_prestige"), rs.getInt("international_prestige"), Math.max(1, rs.getInt("league_level")), rs.getInt("youth_development"),
                        rs.getObject("club_worth_eur", Long.class), rs.getInt("players"), rs.getDouble("avg_ovr"), rs.getLong("top_value"), rs.getInt("young")));
    }

    public List<PlayerRatingEntity> findArchivedCatalogSquad(int clubId, long archivedSquadVersionId) {
        return jdbc.query("""
                SELECT a.* FROM player_rating_archive a JOIN base_player b ON b.id = a.base_player_id
                 WHERE a.squad_version_id = :sv AND a.club_id = :club AND b.owner_career_id IS NULL""",
                new MapSqlParameterSource("sv", archivedSquadVersionId).addValue("club", clubId), (rs, i) -> toArchivedRating(rs));
    }

    private PlayerRatingEntity toArchivedRating(ResultSet rs) throws SQLException {
        PlayerRatingEntity rating = new PlayerRatingEntity();
        rating.setBasePlayerId(rs.getLong("base_player_id"));
        rating.setGameVersionId(rs.getShort("game_version_id"));
        rating.setClubId(rs.getObject("club_id", Integer.class));
        rating.setOverall(rs.getShort("overall"));
        rating.setPositions(Arrays.asList((String[]) rs.getArray("positions").getArray()));
        rating.setAccelerate(rs.getString("accelerate"));
        rating.setWeakFoot(rs.getObject("weak_foot", Short.class));
        rating.setSkillMoves(rs.getObject("skill_moves", Short.class));
        rating.setBodyType(rs.getString("body_type"));
        rating.setPlaystyles(readPlaystyles(rs.getString("playstyles")));
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr attr : Attr.values()) {
            Integer value = rs.getObject(attr.getColumn(), Integer.class);
            if (Objects.nonNull(value)) {
                attrs.put(attr, value);
            }
        }
        ratingAttrAccessor.write(rating, attrs);
        return rating;
    }

    private Map<String, Integer> readPlaystyles(String json) {
        if (Objects.isNull(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("player_rating_archive playstyles unreadable", e);
        }
    }

    private static String ratings(Long archivedSquadVersionId) {
        return Objects.isNull(archivedSquadVersionId) ? "player_rating" : ARCHIVED_RATINGS;
    }

    private static String profiles(Long archivedSquadVersionId) {
        return Objects.isNull(archivedSquadVersionId) ? "club_profile" : ARCHIVED_PROFILES;
    }

    private static MapSqlParameterSource withSquadVersion(MapSqlParameterSource params, Long archivedSquadVersionId) {
        return Objects.isNull(archivedSquadVersionId) ? params : params.addValue("sv", archivedSquadVersionId);
    }

    private static List<Priority> priorities(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((String[]) array.getArray()).map(Priority::valueOf).toList();
    }
}
