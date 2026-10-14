package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.tacticanalysis.LevelSql.Exprs;
import com.fcareer.tacticanalysis.LevelSql.Medians;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Kariyer seviye referansı (E3-7b): kullanıcının liginin (career_player + career_team) aynı mevki medyanları. Katalog tablosu burada geçmez (CareerReadsOnlyCareerPlayerArchTest);
 * kariyer dışı referans CatalogLevelRepository'dedir. Metrikler kural metrikleriyle aynıdır: stoper hızı (Acc+Sprint)/2, pas güvenliği (SP+Comp+BC+Vision)/4 (GK/CB/CDM), hava hedefi (Heading+Jumping+boy)/3 (ST).
 */
@Repository
@RequiredArgsConstructor
public class LevelReferenceRepository {

    private final JdbcTemplate jdbc;

    private static String attr(Attr a) {
        return "cp.attrs[" + (a.ordinal() + 1) + "]";
    }

    public Optional<String> leagueOfTeam(long careerId, int teamId) {
        List<String> rows = jdbc.queryForList("SELECT league_name FROM career_team WHERE career_id = ? AND team_id = ?", String.class, careerId, teamId);
        return rows.stream().filter((n) -> n != null && !n.isBlank()).findFirst();
    }

    /** Kullanıcının liginin (canlı, serbest olmayan oyuncular) aynı mevki medyanları. */
    public Medians leagueMedians(long careerId, String leagueName, double heightMin, double heightMax) {
        String from = " FROM career_player cp JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id WHERE cp.career_id = ? AND cp.live AND NOT cp.free_agent AND ct.league_name = ? AND ";
        Exprs e = new Exprs("(" + attr(Attr.ACCELERATION) + " + " + attr(Attr.SPRINT_SPEED) + ") / 2.0",
                "(" + attr(Attr.SHORT_PASSING) + " + " + attr(Attr.COMPOSURE) + " + " + attr(Attr.BALL_CONTROL) + " + " + attr(Attr.VISION) + ") / 4.0",
                "(" + attr(Attr.HEADING) + " + " + attr(Attr.JUMPING) + " + " + LevelSql.heightScore("cp.height_cm", heightMin, heightMax) + ") / 3.0");
        return LevelSql.medians(jdbc, from, new Object[]{careerId, leagueName}, "cp.positions[1]", e);
    }
}
