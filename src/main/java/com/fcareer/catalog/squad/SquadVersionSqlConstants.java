package com.fcareer.catalog.squad;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SquadVersionSqlConstants {

    static final String COLUMNS = "id, game_version_id, version_no, label, game_build, exported_at, player_count, created_at";

    static final String FIND_BY_ID = "SELECT " + COLUMNS + " FROM squad_version WHERE id = :id";

    static final String FIND_ALL_BY_GAME_VERSION = "SELECT " + COLUMNS + " FROM squad_version WHERE game_version_id = :gameVersionId ORDER BY version_no DESC";

    static final String FIND_LATEST = FIND_ALL_BY_GAME_VERSION + " LIMIT 1";

    static final String IS_LATEST = """
            SELECT EXISTS (SELECT 1 FROM squad_version s WHERE s.id = :id
              AND s.version_no = (SELECT max(o.version_no) FROM squad_version o WHERE o.game_version_id = s.game_version_id))""";

    static final String CREATE = """
            INSERT INTO squad_version (game_version_id, version_no, label, game_build, exported_at, player_count)
            SELECT :gameVersionId, COALESCE(max(version_no), 0) + 1, :label, :gameBuild, :exportedAt, :playerCount
            FROM squad_version WHERE game_version_id = :gameVersionId
            RETURNING\s""" + COLUMNS;

    static final String CREATE_FIRST = """
            INSERT INTO squad_version (game_version_id, version_no, label, player_count)
            SELECT :gameVersionId, 1, :label,
                   (SELECT count(*) FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
                     WHERE r.game_version_id = :gameVersionId AND b.owner_career_id IS NULL)
            ON CONFLICT (game_version_id, version_no) DO NOTHING""";

    static final String FIND_ARCHIVED_CATALOG_SQUAD = """
            SELECT a.* FROM player_rating_archive a JOIN base_player b ON b.id = a.base_player_id
             WHERE a.squad_version_id = :squadVersionId AND a.club_id = :clubId AND b.owner_career_id IS NULL""";

    static final String FIND_ARCHIVED_CATALOG_RATING = """
            SELECT a.* FROM player_rating_archive a JOIN base_player b ON b.id = a.base_player_id
             WHERE a.squad_version_id = :squadVersionId AND a.base_player_id = :basePlayerId AND b.owner_career_id IS NULL""";

    private static final String RATING_COLUMNS = """
            base_player_id, game_version_id, club_id, overall, positions, accelerate, weak_foot, skill_moves, intl_reputation, att_wr, def_wr, body_type,
            acceleration, sprint_speed, agility, balance, reactions, ball_control, dribbling, composure, vision, short_passing, long_passing, crossing,
            curve, fk_accuracy, finishing, shot_power, long_shots, volleys, penalties, att_position, heading, def_awareness, standing_tackle, sliding_tackle,
            interceptions, jumping, stamina, strength, aggression, gk_diving, gk_handling, gk_kicking, gk_positioning, gk_reflexes,
            playstyles, run_style1, run_style2, face_stats""";

    static final String ARCHIVE_RATINGS = "INSERT INTO player_rating_archive (squad_version_id, " + RATING_COLUMNS + ") "
            + "SELECT :squadVersionId, " + RATING_COLUMNS + " FROM player_rating r"
            + " WHERE r.game_version_id = :gameVersionId"
            + " AND EXISTS (SELECT 1 FROM base_player b WHERE b.id = r.base_player_id AND b.owner_career_id IS NULL)";

    static final String ARCHIVE_VALUATIONS = """
            INSERT INTO player_valuation_archive (squad_version_id, base_player_id, game_version_id, potential, value_eur, wage_eur, model_version)
            SELECT :squadVersionId, v.base_player_id, v.game_version_id, v.potential, v.value_eur, v.wage_eur, v.model_version FROM player_valuation v
             WHERE v.game_version_id = :gameVersionId AND v.source = 'MODELED' AND v.owner_user_id IS NULL
               AND EXISTS (SELECT 1 FROM base_player b WHERE b.id = v.base_player_id AND b.owner_career_id IS NULL)""";

    static final String FIND_VERSIONED_RATINGS = "SELECT " + RATING_COLUMNS + " FROM player_rating_archive a"
            + " WHERE a.squad_version_id = :squadVersionId AND a.base_player_id IN (:ids)"
            + " AND EXISTS (SELECT 1 FROM base_player b WHERE b.id = a.base_player_id AND b.owner_career_id IS NULL)"
            + " UNION ALL SELECT " + RATING_COLUMNS + " FROM player_rating r"
            + " WHERE r.game_version_id = :gameVersionId AND r.base_player_id IN (:ids)"
            + " AND EXISTS (SELECT 1 FROM base_player b WHERE b.id = r.base_player_id AND b.owner_career_id = CAST(:careerId AS BIGINT))";

    static final String FIND_VERSIONED_VALUATIONS = """
            SELECT a.base_player_id, a.game_version_id, a.potential, a.value_eur, a.wage_eur, a.model_version FROM player_valuation_archive a
             WHERE a.squad_version_id = :squadVersionId AND a.base_player_id IN (:ids)
            UNION ALL
            SELECT v.base_player_id, v.game_version_id, v.potential, v.value_eur, v.wage_eur, v.model_version FROM player_valuation v
             WHERE v.game_version_id = :gameVersionId AND v.base_player_id IN (:ids) AND v.source = 'MODELED' AND v.owner_user_id IS NULL
               AND EXISTS (SELECT 1 FROM base_player b WHERE b.id = v.base_player_id AND b.owner_career_id = CAST(:careerId AS BIGINT))""";

    static final String FIND_ARCHIVED_SQUAD_VERSION_OF_CAREER = """
            SELECT k.squad_version_id FROM career k JOIN squad_version s ON s.id = k.squad_version_id
             WHERE k.id = :careerId
               AND EXISTS (SELECT 1 FROM squad_version o WHERE o.game_version_id = s.game_version_id AND o.version_no > s.version_no)""";

    private static final String CAREER_ON_LATEST = """
            EXISTS (SELECT 1 FROM career lk JOIN squad_version ls ON ls.id = lk.squad_version_id WHERE lk.id = %1$s
              AND NOT EXISTS (SELECT 1 FROM squad_version lo WHERE lo.game_version_id = ls.game_version_id AND lo.version_no > ls.version_no))""";

    private static final String CAREER_VALUATIONS = """
            (SELECT v.base_player_id, v.game_version_id, v.potential, v.value_eur FROM player_valuation v
              WHERE v.source = 'MODELED' AND v.owner_user_id IS NULL AND %2$s
             UNION ALL
             SELECT a.base_player_id, a.game_version_id, a.potential, a.value_eur FROM player_valuation_archive a
              WHERE a.squad_version_id = (SELECT squad_version_id FROM career WHERE id = %1$s) AND NOT %2$s
             UNION ALL
             SELECT v.base_player_id, v.game_version_id, v.potential, v.value_eur FROM player_valuation v
              WHERE v.source = 'MODELED' AND v.owner_user_id IS NULL AND NOT %2$s
                AND v.base_player_id IN (SELECT id FROM base_player WHERE owner_career_id = %1$s))""";

    public static String buildCareerOnLatestSquadVersionSql(String careerIdExpression) {
        return String.format(CAREER_ON_LATEST, careerIdExpression);
    }

    public static String buildCareerValuationsSql(String careerIdExpression) {
        return String.format(CAREER_VALUATIONS, careerIdExpression, buildCareerOnLatestSquadVersionSql(careerIdExpression));
    }

    private static final String CLUB_PROFILE_COLUMNS = """
            club_id, game_version_id, domestic_prestige, international_prestige, popularity, transfer_budget_eur, club_worth_eur,
            youth_development, league_level, updated_at""";

    static final String ARCHIVE_CLUB_PROFILES = "INSERT INTO club_profile_archive (squad_version_id, " + CLUB_PROFILE_COLUMNS + ") "
            + "SELECT :squadVersionId, " + CLUB_PROFILE_COLUMNS + " FROM club_profile WHERE game_version_id = :gameVersionId";
}
