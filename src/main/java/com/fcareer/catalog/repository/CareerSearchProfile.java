package com.fcareer.catalog.repository;

import com.fcareer.fitengine.model.Attr;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Kariyer araması: yalnız kariyerin kendi oyuncu tablosu (tek doğruluk kaynağı); katalog derece tablosuna dokunmaz. */
final class CareerSearchProfile implements SearchProfile {

    static final CareerSearchProfile INSTANCE = new CareerSearchProfile();

    private static final Attr[] ATTRS = Attr.values();
    private static final String ATTRS_JSON = "jsonb_build_object(" + Arrays.stream(ATTRS).map((a) -> "'" + a.getColumn() + "', cp.attrs[" + (a.ordinal() + 1) + "]")
            .collect(Collectors.joining(", ")) + ")::text";

    private CareerSearchProfile() {
    }

    @Override
    public String from() {
        return """
                 FROM career_player cp
                 JOIN base_player b ON b.id = cp.player_id
                 JOIN career k ON k.id = cp.career_id
                 LEFT JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id
                 LEFT JOIN club c ON c.id = cp.club_id
                 LEFT JOIN league l ON l.id = c.league_id
                 LEFT JOIN nationality n ON n.id = b.nationality_id
                 LEFT JOIN player_valuation v ON v.base_player_id = cp.player_id
                      AND v.game_version_id = k.game_version_id AND v.source = 'MODELED' AND v.owner_user_id IS NULL
                 LEFT JOIN player_valuation vi ON vi.base_player_id = cp.player_id
                      AND vi.game_version_id = k.game_version_id AND vi.source = 'IMPORT' AND vi.owner_user_id = k.user_id
                """;
    }

    @Override
    public String baseFilter() {
        return "cp.career_id = CAST(:careerId AS BIGINT)";
    }

    @Override
    public String overall() {
        return "cp.ovr";
    }

    @Override
    public String potential() {
        return "cp.pot";
    }

    @Override
    public String positions() {
        return "cp.positions";
    }

    @Override
    public String weakFoot() {
        return "cp.weak_foot";
    }

    @Override
    public String skillMoves() {
        return "cp.skill_moves";
    }

    @Override
    public String accelerate() {
        return "cp.accelerate";
    }

    @Override
    public String attr(Attr attr) {
        return "cp.attrs[" + (attr.ordinal() + 1) + "]";
    }

    @Override
    public String playstyles() {
        return "cp.playstyles";
    }

    @Override
    public String clubId() {
        return "c.id";
    }

    @Override
    public String clubName() {
        return "COALESCE(ct.name, c.name)";
    }

    @Override
    public String leagueId() {
        return "c.league_id";
    }

    @Override
    public String leagueName() {
        return "COALESCE(ct.league_name, l.name)";
    }

    @Override
    public String value() {
        return "COALESCE(vi.value_eur, v.value_eur)";
    }

    @Override
    public String teamId() {
        return "cp.team_id";
    }

    @Override
    public String freeAgentFilter() {
        return "cp.free_agent";
    }

    @Override
    public String freeAgentSelect() {
        return "(cp.free_agent OR l.name = 'Free Agents')";
    }

    @Override
    public String estimated() {
        return "(NOT cp.live AND vi.id IS NULL AND v.id IS NOT NULL)";
    }

    @Override
    public String runStyle() {
        return "cp.run_style1";
    }

    @Override
    public String faceOfficial() {
        return "cp.face_stats";
    }

    @Override
    public String contractUntil() {
        return "cp.contract_until";
    }

    @Override
    public String availableFilter() {
        return "cp.available";
    }

    @Override
    public String livePoolFilter() {
        // Korelasyonsuz alt sorgu: tek sefer hesaplanır (eski EXISTS her satır için tarıyordu; katalog kariyerinde O(n²), 55 sn).
        return "(cp.live OR NOT COALESCE((SELECT bool_or(lv.live) FROM career_player lv WHERE lv.career_id = CAST(:careerId AS BIGINT)), FALSE))";
    }

    @Override
    public String attrsJson() {
        return ATTRS_JSON;
    }

    @Override
    public String gameDate() {
        return "COALESCE(k.current_game_date, CURRENT_DATE)";
    }
}
