package com.fcareer.career.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Kadro hareketleri için ham satırlar: kariyer kaydındaki (career_player, yalnız içe aktarılan "live" satırlar) kulüp/kiralık ile oyunun ana
 * kaydındaki kulüp (career_player.base_club_id, yazım anında temel katalogdan kopyalanır; kariyer okuması kataloğa dokunmaz). Yalnız kullanıcının
 * kulübünü ilgilendiren satırlar döner; sınıflandırma CareerMovesService'te.
 */
@Repository
@RequiredArgsConstructor
public class CareerMovesRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Row(long playerId, Long eaId, String name, Integer overall, String position, boolean generated,
                      Integer careerClubId, String careerClub, Integer baseClubId, String baseClub, Integer loanFromTeamId, String loanFromClub,
                      boolean loanFromUs, boolean freeAgent, LocalDate loanUntil) {
    }

    public List<Row> rows(long careerId) {
        return jdbc.query("""
                WITH k AS (SELECT id, club_id, (SELECT ea_team_id FROM club WHERE id = career.club_id) AS us_team FROM career WHERE id = :c)
                SELECT cp.player_id, b.ea_resource_id, b.full_name, cp.ovr, cp.positions[1] AS position, (b.owner_career_id IS NOT NULL) AS generated,
                       cp.club_id AS career_club_id, cc.name AS career_club, cp.base_club_id, bc.name AS base_club,
                       cp.loan_from_team_id, lc.name AS loan_from_club, COALESCE(cp.loan_from_team_id = k.us_team, false) AS loan_from_us,
                       cp.free_agent, cp.loan_until
                  FROM career_player cp
                  JOIN k ON k.id = cp.career_id
                  JOIN base_player b ON b.id = cp.player_id
                  LEFT JOIN club cc ON cc.id = cp.club_id
                  LEFT JOIN club bc ON bc.id = cp.base_club_id
                  LEFT JOIN LATERAL (SELECT name FROM club WHERE ea_team_id = cp.loan_from_team_id ORDER BY id LIMIT 1) lc ON cp.loan_from_team_id IS NOT NULL
                 WHERE cp.career_id = :c AND cp.live
                   AND (cp.club_id = k.club_id OR cp.base_club_id = k.club_id OR cp.loan_from_team_id = k.us_team)
                 ORDER BY cp.ovr DESC NULLS LAST, b.full_name
                """, Map.of("c", careerId), (rs, i) -> new Row(
                rs.getLong("player_id"), rs.getObject("ea_resource_id", Long.class), rs.getString("full_name"), rs.getObject("ovr", Integer.class),
                rs.getString("position"), rs.getBoolean("generated"),
                rs.getObject("career_club_id", Integer.class), rs.getString("career_club"), rs.getObject("base_club_id", Integer.class), rs.getString("base_club"),
                rs.getObject("loan_from_team_id", Integer.class), rs.getString("loan_from_club"), rs.getBoolean("loan_from_us"),
                rs.getBoolean("free_agent"), rs.getObject("loan_until", LocalDate.class)));
    }
}
