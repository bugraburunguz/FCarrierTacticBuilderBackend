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

    /** Kariyer verisi sürümü: içe aktarma/düzenlemede artar; liste önbelleğinin anahtarıdır. */
    public long careerDataVersion(long careerId) {
        Long version = jdbc.queryForObject("SELECT data_version FROM career WHERE id = :c", Map.of("c", careerId), Long.class);
        return Objects.isNull(version) ? 0 : version;
    }

    /** Kariyer listesinin hangi süzgeçleri anlamlı: oyun tarihi, bütçe (NULL = bilinmiyor, ENG-02), sözleşme ve kiralık verisi var mı. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    /** squadWageEur (V3-06d): kadrodaki herkesin maaşı kayıtta varsa toplamı, biri bile eksikse yok (ENG-02). */
    public record CareerFacets(java.time.LocalDate gameDate, Long budgetEur, boolean contractData, boolean loanData, String leagueName, Integer leagueOvr, Long squadWageEur) {
        public CareerFacets withLeague(String name, int ovr) {
            return new CareerFacets(gameDate, budgetEur, contractData, loanData, name, ovr, squadWageEur);
        }
    }

    public CareerFacets careerFacets(long careerId) {
        return jdbc.queryForObject("""
                SELECT k.current_game_date, k.budget_eur,
                       EXISTS (SELECT 1 FROM career_player cp WHERE cp.career_id = k.id AND cp.contract_until IS NOT NULL) AS contract_data,
                       EXISTS (SELECT 1 FROM career_player cp WHERE cp.career_id = k.id AND cp.loan_from_team_id IS NOT NULL) AS loan_data,
                       (SELECT CASE WHEN count(*) > 0 AND count(cp.wage_eur) = count(*) THEN sum(cp.wage_eur) END
                          FROM user_player_state s LEFT JOIN career_player cp ON cp.career_id = s.career_id AND cp.player_id = s.base_player_id
                         WHERE s.career_id = k.id) AS squad_wage
                  FROM career k WHERE k.id = :c""", Map.of("c", careerId), (rs, i) -> new CareerFacets(
                Objects.isNull(rs.getDate("current_game_date")) ? null : rs.getDate("current_game_date").toLocalDate(),
                rs.getObject("budget_eur") == null ? null : rs.getLong("budget_eur"), rs.getBoolean("contract_data"), rs.getBoolean("loan_data"), null, null,
                rs.getObject("squad_wage") == null ? null : rs.getLong("squad_wage")));
    }

    /** V3-06b: oyuncu sayfasının sözleşme satırları; kayıtta olmayan alan null kalır (ENG-02). */
    /** loanedOut: kiralık kaynağı kullanıcının kulübü (oyuncu bizden kiralık gitmiş); onLoan her iki yönde de true. */
    public record Contract(Long wageEur, Long releaseClauseEur, java.time.LocalDate contractUntil, boolean onLoan, boolean loanedOut) {
    }

    public java.util.Optional<Contract> contract(long careerId, long playerId) {
        return jdbc.query("""
                SELECT cp.wage_eur, cp.release_clause_eur, cp.contract_until, cp.loan_from_team_id IS NOT NULL AS on_loan,
                       COALESCE(cp.loan_from_team_id = (SELECT c.ea_team_id FROM career k JOIN club c ON c.id = k.club_id WHERE k.id = cp.career_id), false) AS loaned_out
                  FROM career_player cp WHERE cp.career_id = :c AND cp.player_id = :p""", Map.of("c", careerId, "p", playerId), (rs, i) -> new Contract(
                rs.getObject("wage_eur") == null ? null : rs.getLong("wage_eur"),
                rs.getObject("release_clause_eur") == null ? null : rs.getLong("release_clause_eur"),
                Objects.isNull(rs.getDate("contract_until")) ? null : rs.getDate("contract_until").toLocalDate(), rs.getBoolean("on_loan"),
                rs.getBoolean("loaned_out"))).stream().findFirst();
    }

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
        // Tohumlanmamış havuzdan boş güç hesaplanıp data_version anahtarıyla önbellekte kalmasın (V3-06c): byIds ile aynı tohumlama.
        careerPlayerWriter.ensureSeeded(careerId);
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
                     AND (cp.live OR NOT (SELECT COALESCE(bool_or(lv.live), FALSE) FROM career_player lv WHERE lv.career_id = :c))
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
