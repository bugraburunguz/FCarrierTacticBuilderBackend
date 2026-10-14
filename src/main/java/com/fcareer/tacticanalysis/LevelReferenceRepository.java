package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Seviye referansı sorguları (E3-7b): aynı mevki oyuncularının metrik medyanı. Kariyerde kullanıcının ligi (career_player + career_team), kariyer dışında OVR bandındaki katalog (player_rating).
 * Metrikler kural metrikleriyle birebir aynıdır: stoper hızı (Acc+Sprint)/2, pas güvenliği (SP+Comp+BC+Vision)/4 (GK/CB/CDM), hava hedefi (Heading+Jumping+boy skoru)/3 (ST).
 */
@Repository
@RequiredArgsConstructor
public class LevelReferenceRepository {

    public record Median(Double value, int n) {
    }

    public record Medians(Median cbPace, Median passerSafety, Median aerial) {
    }

    private record Exprs(String pace, String passer, String aerial) {
    }

    private final JdbcTemplate jdbc;

    private static String careerAttr(Attr a) {
        return "cp.attrs[" + (a.ordinal() + 1) + "]";
    }

    private static String catalogAttr(Attr a) {
        return "r." + a.getColumn();
    }

    private static String heightScore(String column, double hmin, double hmax) {
        return "(CASE WHEN " + column + " IS NULL THEN 50.0 ELSE LEAST(1.0, GREATEST(0.0, (" + column + " - " + hmin + ") / " + (hmax - hmin) + ")) * 100.0 END)";
    }

    public Optional<String> leagueOfTeam(long careerId, int teamId) {
        List<String> rows = jdbc.queryForList("SELECT league_name FROM career_team WHERE career_id = ? AND team_id = ?", String.class, careerId, teamId);
        return rows.stream().filter((n) -> n != null && !n.isBlank()).findFirst();
    }

    /** Kullanıcının liginin (canlı, serbest olmayan oyuncular) aynı mevki medyanları. */
    public Medians leagueMedians(long careerId, String leagueName, double heightMin, double heightMax) {
        String from = " FROM career_player cp JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id WHERE cp.career_id = ? AND cp.live AND NOT cp.free_agent AND ct.league_name = ? AND ";
        Exprs e = new Exprs("(" + careerAttr(Attr.ACCELERATION) + " + " + careerAttr(Attr.SPRINT_SPEED) + ") / 2.0",
                "(" + careerAttr(Attr.SHORT_PASSING) + " + " + careerAttr(Attr.COMPOSURE) + " + " + careerAttr(Attr.BALL_CONTROL) + " + " + careerAttr(Attr.VISION) + ") / 4.0",
                "(" + careerAttr(Attr.HEADING) + " + " + careerAttr(Attr.JUMPING) + " + " + heightScore("cp.height_cm", heightMin, heightMax) + ") / 3.0");
        return medians(from, new Object[]{careerId, leagueName}, "cp.positions[1]", e);
    }

    /** Katalog: OVR bandındaki oyuncuların aynı mevki medyanları (fit_level_norms mantığı). */
    public Medians catalogMedians(short gameVersionId, int ovrLo, int ovrHi, double heightMin, double heightMax) {
        String from = " FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.game_version_id = ? AND r.overall BETWEEN ? AND ? AND b.owner_career_id IS NULL AND b.retired_at IS NULL AND ";
        Exprs e = new Exprs("(" + catalogAttr(Attr.ACCELERATION) + " + " + catalogAttr(Attr.SPRINT_SPEED) + ") / 2.0",
                "(" + catalogAttr(Attr.SHORT_PASSING) + " + " + catalogAttr(Attr.COMPOSURE) + " + " + catalogAttr(Attr.BALL_CONTROL) + " + " + catalogAttr(Attr.VISION) + ") / 4.0",
                "(" + catalogAttr(Attr.HEADING) + " + " + catalogAttr(Attr.JUMPING) + " + " + heightScore("b.height_cm", heightMin, heightMax) + ") / 3.0");
        return medians(from, new Object[]{gameVersionId, ovrLo, ovrHi}, "r.positions[1]", e);
    }

    private Medians medians(String from, Object[] args, String primaryPosition, Exprs e) {
        return new Medians(median(e.pace(), from, args, primaryPosition + " = 'CB'"), median(e.passer(), from, args, primaryPosition + " IN ('GK', 'CB', 'CDM')"),
                median(e.aerial(), from, args, primaryPosition + " = 'ST'"));
    }

    private Median median(String expr, String from, Object[] args, String positionFilter) {
        return jdbc.query("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY " + expr + ") AS med, count(*) AS n" + from + positionFilter, (rs) -> {
            rs.next();
            return new Median(rs.getObject("med") == null ? null : rs.getDouble("med"), rs.getInt("n"));
        }, args);
    }
}
