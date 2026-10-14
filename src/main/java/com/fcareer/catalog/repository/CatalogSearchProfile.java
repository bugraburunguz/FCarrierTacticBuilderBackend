package com.fcareer.catalog.repository;

import com.fcareer.fitengine.model.Attr;

/** Kariyer dışı arama: EA kataloğu (player_rating) ve modellenmiş değerler. */
final class CatalogSearchProfile implements SearchProfile {

    static final CatalogSearchProfile INSTANCE = new CatalogSearchProfile();

    private CatalogSearchProfile() {
    }

    @Override
    public String from() {
        return """
                 FROM player_rating r
                 JOIN base_player b ON b.id = r.base_player_id
                 LEFT JOIN club c ON c.id = r.club_id
                 LEFT JOIN league l ON l.id = c.league_id
                 LEFT JOIN nationality n ON n.id = b.nationality_id
                 LEFT JOIN player_valuation v ON v.base_player_id = r.base_player_id
                      AND v.game_version_id = r.game_version_id AND v.source = 'MODELED' AND v.owner_user_id IS NULL
                """;
    }

    @Override
    public String baseFilter() {
        return "r.game_version_id = :gv AND b.owner_career_id IS NULL";
    }

    @Override
    public String overall() {
        return "r.overall";
    }

    @Override
    public String potential() {
        return "v.potential";
    }

    @Override
    public String positions() {
        return "r.positions";
    }

    @Override
    public String weakFoot() {
        return "r.weak_foot";
    }

    @Override
    public String skillMoves() {
        return "r.skill_moves";
    }

    @Override
    public String accelerate() {
        return "r.accelerate";
    }

    @Override
    public String attr(Attr attr) {
        return "r." + attr.getColumn();
    }

    @Override
    public String playstyles() {
        return "r.playstyles";
    }

    @Override
    public String clubId() {
        return "c.id";
    }

    @Override
    public String clubName() {
        return "c.name";
    }

    @Override
    public String leagueId() {
        return "c.league_id";
    }

    @Override
    public String leagueName() {
        return "l.name";
    }

    @Override
    public String value() {
        return "v.value_eur";
    }

    @Override
    public String teamId() {
        return "CAST(NULL AS INTEGER)";
    }

    @Override
    public String freeAgentFilter() {
        return "FALSE";
    }

    @Override
    public String freeAgentSelect() {
        return "(l.name = 'Free Agents')";
    }

    @Override
    public String estimated() {
        return "(v.id IS NOT NULL)";
    }

    @Override
    public String runStyle() {
        return "r.run_style1";
    }

    @Override
    public String faceOfficial() {
        return "r.face_stats";
    }

    @Override
    public String contractUntil() {
        return "CAST(NULL AS DATE)";
    }

    @Override
    public String availableFilter() {
        return "TRUE";
    }

    @Override
    public String livePoolFilter() {
        return "TRUE";
    }

    @Override
    public String attrsJson() {
        return "to_jsonb(r)::text";
    }

    @Override
    public String gameDate() {
        return "CURRENT_DATE";
    }
}
