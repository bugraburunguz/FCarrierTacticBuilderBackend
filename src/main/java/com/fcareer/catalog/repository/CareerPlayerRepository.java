package com.fcareer.catalog.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.base.CareerPlayerWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Kariyer oyuncusunun tek doğruluk kaynağı (career_player) okuması: derece/aşinalık/özellikler. Katalog tablosuna dokunmaz. */
@Repository
@RequiredArgsConstructor
public class CareerPlayerRepository {

    /** career_player satırının motor ve detay ekranları için gereken kısmı. */
    public record Row(long playerId, int ovr, Integer pot, List<String> positions, Map<Attr, Integer> attrs, Map<String, Integer> playstyles, String accelerate,
                      Integer weakFoot, Integer skillMoves, int runStyle1, int runStyle2, List<Integer> faceStats, String bodyType, Map<String, Integer> roleFamiliarity,
                      List<String> hiddenTraits) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CareerPlayerWriter careerPlayerWriter;

    public Map<Long, Row> byIds(long careerId, Collection<Long> playerIds) {
        Map<Long, Row> out = new HashMap<>();
        if (playerIds.isEmpty()) {
            return out;
        }
        careerPlayerWriter.ensureSeeded(careerId);
        jdbc.query("""
                SELECT player_id, ovr, pot, positions, attrs, playstyles::text AS playstyles, accelerate, weak_foot, skill_moves, run_style1, run_style2, face_stats,
                       body_type, role_familiarity::text AS role_familiarity, hidden_traits
                  FROM career_player WHERE career_id = :c AND player_id IN (:ids)""", new MapSqlParameterSource("c", careerId).addValue("ids", playerIds), (rs) -> {
            try {
                Short[] attrValues = (Short[]) rs.getArray("attrs").getArray();
                Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
                Attr[] all = Attr.values();
                for (int i = 0; i < all.length && i < attrValues.length; i++) {
                    attrs.put(all[i], attrValues[i].intValue());
                }
                Array faces = rs.getArray("face_stats");
                Array traits = rs.getArray("hidden_traits");
                out.put(rs.getLong("player_id"), new Row(rs.getLong("player_id"), rs.getInt("ovr"), (Integer) rs.getObject("pot"),
                        Arrays.asList((String[]) rs.getArray("positions").getArray()), attrs, readMap(rs.getString("playstyles")), rs.getString("accelerate"),
                        (Integer) rs.getObject("weak_foot"), (Integer) rs.getObject("skill_moves"), rs.getInt("run_style1"), rs.getInt("run_style2"),
                        Objects.isNull(faces) ? null : Arrays.stream((Object[]) faces.getArray()).map((v) -> ((Number) v).intValue()).toList(), rs.getString("body_type"),
                        readMap(rs.getString("role_familiarity")), Objects.isNull(traits) ? List.of() : Arrays.asList((String[]) traits.getArray())));
            } catch (SQLException e) {
                throw new IllegalStateException("career_player unreadable", e);
            }
        });
        return out;
    }

    /**
     * Öneri fizibilitesi için kariyer satırı ekleri: takım (LE), katalog kulübü, sözleşme bitişi, kulüp içi OVR sırası, kulübün lig seviyesi (league.tier, bilinen ligler) ve lig adı.
     * Takım anahtarı: kayıtta (live) oyuncu için "T:" + team_id, değilse "C:" + club_id.
     */
    public record Extra(long playerId, Integer teamId, Integer clubId, java.time.LocalDate contractUntil, Integer clubRank, Integer leagueTier, String leagueName) {
        public String teamKey() {
            if (Objects.nonNull(teamId)) {
                return "T:" + teamId;
            }
            return Objects.nonNull(clubId) ? "C:" + clubId : null;
        }
    }

    public Map<Long, Extra> extras(long careerId, Collection<Long> playerIds) {
        Map<Long, Extra> out = new HashMap<>();
        if (playerIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT cp.player_id, cp.team_id, cp.club_id, cp.contract_until, ranked.rk, l.tier,
                       CASE WHEN cp.team_id IS NOT NULL THEN COALESCE(ct.league_name, l.name) ELSE l.name END AS league_name
                  FROM career_player cp
                  LEFT JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id
                  LEFT JOIN club c ON c.id = cp.club_id
                  LEFT JOIN league l ON l.id = c.league_id
                  LEFT JOIN (SELECT player_id, rank() OVER (PARTITION BY team_id ORDER BY ovr DESC) AS rk
                               FROM career_player WHERE career_id = :c AND live AND team_id IS NOT NULL) ranked ON ranked.player_id = cp.player_id
                 WHERE cp.career_id = :c AND cp.player_id IN (:ids)""", new MapSqlParameterSource("c", careerId).addValue("ids", playerIds), (rs) -> {
            java.sql.Date contract = rs.getDate("contract_until");
            out.put(rs.getLong("player_id"), new Extra(rs.getLong("player_id"), (Integer) rs.getObject("team_id"), (Integer) rs.getObject("club_id"),
                    Objects.isNull(contract) ? null : contract.toLocalDate(), Objects.isNull(rs.getObject("rk")) ? null : ((Number) rs.getObject("rk")).intValue(),
                    Objects.isNull(rs.getObject("tier")) ? null : ((Number) rs.getObject("tier")).intValue(), rs.getString("league_name")));
        });
        return out;
    }

    /** Kulüp gücü hesabı için ham satırlar: takım anahtarı, lig adı, en iyi N oyuncunun OVR ortalaması ve oyuncu sayısı (serbest havuz ve kayıtta olmayan oyuncular hariç). */
    public record TeamStrengthRow(String key, String league, double strength, int players) {
    }

    public List<TeamStrengthRow> teamStrengths(long careerId, int top) {
        return jdbc.query("""
                SELECT kt || ':' || k AS team_key, league, avg(ovr)::float8 AS strength, count(*) AS n FROM (
                  SELECT CASE WHEN cp.live AND cp.team_id IS NOT NULL THEN 'T' ELSE 'C' END AS kt,
                         CASE WHEN cp.live AND cp.team_id IS NOT NULL THEN cp.team_id ELSE cp.club_id END AS k,
                         CASE WHEN cp.live AND cp.team_id IS NOT NULL THEN ct.league_name ELSE l.name END AS league,
                         cp.ovr,
                         row_number() OVER (PARTITION BY (CASE WHEN cp.live AND cp.team_id IS NOT NULL THEN 'T' ELSE 'C' END),
                                                         (CASE WHEN cp.live AND cp.team_id IS NOT NULL THEN cp.team_id ELSE cp.club_id END) ORDER BY cp.ovr DESC) AS rn
                    FROM career_player cp
                    LEFT JOIN career_team ct ON ct.career_id = cp.career_id AND ct.team_id = cp.team_id
                    LEFT JOIN club c ON c.id = cp.club_id
                    LEFT JOIN league l ON l.id = c.league_id
                   WHERE cp.career_id = :c AND NOT cp.free_agent AND NOT COALESCE(ct.free_agent_pool, FALSE)
                     AND (cp.team_id IS NOT NULL OR cp.club_id IS NOT NULL)
                     AND (cp.live OR NOT EXISTS (SELECT 1 FROM career_player lv WHERE lv.career_id = cp.career_id AND lv.live))
                ) s WHERE rn <= :top GROUP BY kt, k, league""", new MapSqlParameterSource("c", careerId).addValue("top", top),
                (rs, i) -> new TeamStrengthRow(rs.getString("team_key"), rs.getString("league"), rs.getDouble("strength"), rs.getInt("n")));
    }

    /** Bu oyuncuların çoğunun oynadığı takımın anahtarı (kullanıcı takımı tahmini); kayıtta takımı olan yoksa boş. */
    public java.util.Optional<String> dominantTeamKey(long careerId, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<Integer> teams = jdbc.queryForList("""
                SELECT team_id FROM career_player WHERE career_id = :c AND live AND team_id IS NOT NULL AND player_id IN (:ids)
                 GROUP BY team_id ORDER BY count(*) DESC, team_id LIMIT 1""", new MapSqlParameterSource("c", careerId).addValue("ids", playerIds), Integer.class);
        return teams.stream().findFirst().map((id) -> "T:" + id);
    }

    private Map<String, Integer> readMap(String json) {
        try {
            return Objects.isNull(json) ? Map.of() : objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (IOException e) {
            throw new IllegalStateException("career_player json", e);
        }
    }
}
