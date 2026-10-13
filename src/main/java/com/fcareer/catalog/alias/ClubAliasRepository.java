package com.fcareer.catalog.alias;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** club_alias sorguları ve tohum yazımı (SQL yalnız burada). */
@Repository
@RequiredArgsConstructor
public class ClubAliasRepository {

    private final JdbcTemplate jdbc;

    /** Ad + lig + cinsiyetle eşleşen (lig/cinsiyet null olan alias her biriyle eşleşir) kanonik club id'si. */
    public Optional<Integer> findClubId(String aliasName, Integer leagueId, int gender) {
        List<Integer> ids = jdbc.queryForList("""
                SELECT club_id FROM club_alias
                WHERE lower(alias_name) = lower(?) AND (league_id IS NULL OR league_id = ?) AND (gender IS NULL OR gender = ?)
                ORDER BY (league_id IS NULL), (gender IS NULL) LIMIT 1""", Integer.class, aliasName, leagueId, gender);
        return ids.stream().findFirst();
    }

    public void upsert(String aliasName, Integer leagueId, Integer gender, Integer eaTeamId, int clubId, String source, boolean verified) {
        jdbc.update("""
                INSERT INTO club_alias (alias_name, league_id, gender, ea_team_id, club_id, source, verified) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (lower(alias_name), COALESCE(league_id, 0), COALESCE(gender, 2))
                DO UPDATE SET club_id = EXCLUDED.club_id, ea_team_id = EXCLUDED.ea_team_id, source = EXCLUDED.source, verified = EXCLUDED.verified""",
                aliasName, leagueId, gender, eaTeamId, clubId, source, verified);
    }
}
