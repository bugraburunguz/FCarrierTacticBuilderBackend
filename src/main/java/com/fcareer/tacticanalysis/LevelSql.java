package com.fcareer.tacticanalysis;

import org.springframework.jdbc.core.JdbcTemplate;

/** Seviye referansı SQL parçaları (tablo adı içermez; kariyer ve katalog sorguları ayrı depolarda): medyan hesabı ve boy skoru. */
final class LevelSql {

    record Median(Double value, int n) {
    }

    record Medians(Median cbPace, Median passerSafety, Median aerial) {
    }

    /** SQL ifadeleri: stoper hızı, pas güvenliği, hava hedefi. */
    record Exprs(String pace, String passer, String aerial) {
    }

    private LevelSql() {
    }

    static String heightScore(String column, double hmin, double hmax) {
        return "(CASE WHEN " + column + " IS NULL THEN 50.0 ELSE LEAST(1.0, GREATEST(0.0, (" + column + " - " + hmin + ") / " + (hmax - hmin) + ")) * 100.0 END)";
    }

    static Medians medians(JdbcTemplate jdbc, String from, Object[] args, String primaryPosition, Exprs e) {
        return new Medians(median(jdbc, e.pace(), from, args, primaryPosition + " = 'CB'"), median(jdbc, e.passer(), from, args, primaryPosition + " IN ('GK', 'CB', 'CDM')"),
                median(jdbc, e.aerial(), from, args, primaryPosition + " = 'ST'"));
    }

    private static Median median(JdbcTemplate jdbc, String expr, String from, Object[] args, String positionFilter) {
        return jdbc.query("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY " + expr + ") AS med, count(*) AS n" + from + positionFilter, (rs) -> {
            rs.next();
            return new Median(rs.getObject("med") == null ? null : rs.getDouble("med"), rs.getInt("n"));
        }, args);
    }
}
