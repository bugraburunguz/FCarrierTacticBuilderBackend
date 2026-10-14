package com.fcareer.catalog.repository;

import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * Kariyer dışı (UT / katalog) seviye referansı (E3-7b): takımın ilk 11 ortalama OVR bandındaki katalog oyuncularının aynı mevki medyanları (fit_level_norms mantığı).
 */
@Repository
@RequiredArgsConstructor
public class CatalogLevelRepository {

    public record Median(Double value, int n) {
    }

    public record Medians(Median cbPace, Median passerSafety, Median aerial) {
    }

    private final JdbcTemplate jdbc;

    private static String attr(Attr a) {
        return "r." + a.getColumn();
    }

    private static String heightScore(double hmin, double hmax) {
        return "(CASE WHEN b.height_cm IS NULL THEN 50.0 ELSE LEAST(1.0, GREATEST(0.0, (b.height_cm - " + hmin + ") / " + (hmax - hmin) + ")) * 100.0 END)";
    }

    public Medians medians(short gameVersionId, int ovrLo, int ovrHi, double heightMin, double heightMax) {
        String from = " FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.game_version_id = ? AND r.overall BETWEEN ? AND ? AND b.owner_career_id IS NULL AND b.retired_at IS NULL AND ";
        Object[] args = {gameVersionId, ovrLo, ovrHi};
        String pace = "(" + attr(Attr.ACCELERATION) + " + " + attr(Attr.SPRINT_SPEED) + ") / 2.0";
        String passer = "(" + attr(Attr.SHORT_PASSING) + " + " + attr(Attr.COMPOSURE) + " + " + attr(Attr.BALL_CONTROL) + " + " + attr(Attr.VISION) + ") / 4.0";
        String aerial = "(" + attr(Attr.HEADING) + " + " + attr(Attr.JUMPING) + " + " + heightScore(heightMin, heightMax) + ") / 3.0";
        return new Medians(median(pace, from, args, "r.positions[1] = 'CB'"), median(passer, from, args, "r.positions[1] IN ('GK', 'CB', 'CDM')"), median(aerial, from, args, "r.positions[1] = 'ST'"));
    }

    private Median median(String expr, String from, Object[] args, String positionFilter) {
        return jdbc.query("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY " + expr + ") AS med, count(*) AS n" + from + positionFilter, (rs) -> {
            rs.next();
            return new Median(Objects.isNull(rs.getObject("med")) ? null : rs.getDouble("med"), rs.getInt("n"));
        }, args);
    }
}
