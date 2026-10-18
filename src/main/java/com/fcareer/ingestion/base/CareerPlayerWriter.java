package com.fcareer.ingestion.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Kariyerin tek doğruluk kaynağı `career_player`'ı yazar (E3-2). Katalog (`player_rating`) ve LE overlay'inden (`career_player_data`, `career_player_rating`)
 * TÜRETİR: içe aktarıcı ve masa düzenlemeleri eski tabloları yazmaya devam eder (çift yazma), ardından bu sınıf kariyer satırlarını tazeler.
 * Katalog okuması yalnız burada ve yalnız yazma anında yapılır; çalışma zamanındaki kariyer okumaları katalog yedeğine düşmez.
 * Yeniden çalıştırmak güvenlidir (upsert); sözleşme/değer/aşinalık gibi LE'ye özgü kolonlar tazelemede korunur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareerPlayerWriter {

    private static final LocalDate LE_EPOCH = LocalDate.of(1582, 10, 14);
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;
    private static final int SEASON_END_MONTH = 6;
    private static final int SEASON_END_DAY = 30;
    private static final String ATTRS_FROM_OVERLAY = Arrays.stream(Attr.values())
            .map((a) -> "COALESCE(cpr." + a.getColumn() + ", r." + a.getColumn() + ")").collect(Collectors.joining(", "));

    private static final String SELECT = """
            SELECT CAST(:c AS BIGINT) AS career_id, b.id AS player_id, COALESCE(k.current_game_date, CURRENT_DATE) AS as_of,
                   cpd.team_id AS team_id, COALESCE(cpd.free_agent, false) AS free_agent, cpd.loan_from_team_id AS loan_from_team_id,
                   CASE WHEN cpd.base_player_id IS NOT NULL THEN tc.id ELSE r.club_id END AS club_id,
                   CASE WHEN b.owner_career_id IS NULL THEN r.club_id END AS base_club_id,
                   (cpd.base_player_id IS NOT NULL) AS live,
                   b.dob AS dob, b.height_cm AS height_cm, b.weight_kg AS weight_kg,
                   CASE lower(left(b.preferred_foot, 1)) WHEN 'r' THEN 'R' WHEN 'l' THEN 'L' END AS preferred_foot,
                   COALESCE(cpr.weak_foot, r.weak_foot) AS weak_foot, COALESCE(cpr.skill_moves, r.skill_moves) AS skill_moves,
                   COALESCE(cpr.accelerate, r.accelerate) AS accelerate, COALESCE(cpr.body_type, r.body_type) AS body_type,
                   COALESCE(cpd.overall, r.overall) AS ovr, COALESCE(cpd.potential, v.potential) AS pot,
                   COALESCE(cpr.positions, r.positions) AS positions,
                   ARRAY[""" + ATTRS_FROM_OVERLAY + """
            ]::smallint[] AS attrs,
                   CASE WHEN cpr.id IS NOT NULL THEN cpr.playstyles ELSE r.playstyles END AS playstyles,
                   COALESCE(cpr.run_style1, r.run_style1) AS run_style1, COALESCE(cpr.run_style2, r.run_style2) AS run_style2,
                   CASE WHEN cpr.id IS NOT NULL THEN cpr.face_stats ELSE r.face_stats END AS face_stats
              FROM player_rating r
              JOIN base_player b ON b.id = r.base_player_id
              JOIN career k ON k.id = :c
              LEFT JOIN career_player_data cpd ON cpd.base_player_id = r.base_player_id AND cpd.career_id = k.id
              LEFT JOIN career_player_rating cpr ON cpr.base_player_id = r.base_player_id AND cpr.career_id = k.id AND cpr.game_version_id = r.game_version_id
              LEFT JOIN LATERAL (SELECT id FROM club WHERE ea_team_id = cpd.team_id ORDER BY id LIMIT 1) tc ON cpd.base_player_id IS NOT NULL
              LEFT JOIN player_valuation v ON v.base_player_id = r.base_player_id AND v.game_version_id = r.game_version_id AND v.source = 'MODELED' AND v.owner_user_id IS NULL
             WHERE r.game_version_id = k.game_version_id AND (b.owner_career_id IS NULL OR b.owner_career_id = k.id) AND b.retired_at IS NULL
            """;

    private static final String COLUMNS = "career_id, player_id, as_of, team_id, free_agent, loan_from_team_id, club_id, base_club_id, live, dob, height_cm, weight_kg, preferred_foot, weak_foot, "
            + "skill_moves, accelerate, body_type, ovr, pot, positions, attrs, playstyles, run_style1, run_style2, face_stats";
    private static final String UPDATE_SET = """
            as_of = EXCLUDED.as_of, team_id = EXCLUDED.team_id, free_agent = EXCLUDED.free_agent, loan_from_team_id = EXCLUDED.loan_from_team_id, club_id = EXCLUDED.club_id, base_club_id = EXCLUDED.base_club_id, live = EXCLUDED.live,
            dob = EXCLUDED.dob, height_cm = EXCLUDED.height_cm, weight_kg = EXCLUDED.weight_kg, preferred_foot = EXCLUDED.preferred_foot, weak_foot = EXCLUDED.weak_foot,
            skill_moves = EXCLUDED.skill_moves, accelerate = EXCLUDED.accelerate, body_type = EXCLUDED.body_type, ovr = EXCLUDED.ovr, pot = EXCLUDED.pot,
            positions = EXCLUDED.positions, attrs = EXCLUDED.attrs, playstyles = EXCLUDED.playstyles, run_style1 = EXCLUDED.run_style1, run_style2 = EXCLUDED.run_style2,
            face_stats = EXCLUDED.face_stats""";
    private static final String UPSERT = "INSERT INTO career_player (" + COLUMNS + ") SELECT " + COLUMNS + " FROM (" + SELECT + ") src %s ON CONFLICT (career_id, player_id) DO UPDATE SET " + UPDATE_SET;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LePlaystyleBits playstyleBits;
    private final PlatformTransactionManager transactionManager;

    /** Kariyerin tüm satırlarını eski tablolardan tazeler; artık kapsamda olmayanları (emekli vb.) düşürür. data_version artar. */
    public int syncCareer(long careerId) {
        return syncCareer(careerId, true);
    }

    private int syncCareer(long careerId, boolean bump) {
        int upserted = jdbc.update(String.format(UPSERT, ""), Map.of("c", careerId));
        jdbc.update("""
                DELETE FROM career_player cp WHERE cp.career_id = :c AND NOT EXISTS (
                  SELECT 1 FROM base_player b WHERE b.id = cp.player_id AND b.retired_at IS NULL AND (b.owner_career_id IS NULL OR b.owner_career_id = :c))""", Map.of("c", careerId));
        if (bump) {
            bumpVersion(careerId);
        }
        log.info("career_player synced careerId : {} rows : {}", careerId, upserted);
        return upserted;
    }

    /** Yalnız verilen oyuncuları tazeler (masa düzenlemeleri). */
    public int syncPlayers(long careerId, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return 0;
        }
        int upserted = jdbc.update(String.format(UPSERT, "WHERE player_id IN (:ids)"), new MapSqlParameterSource("c", careerId).addValue("ids", playerIds));
        bumpVersion(careerId);
        return upserted;
    }

    /** Satırı hiç olmayan kariyeri (içe aktarma yapılmamış ya da geçiş öncesi) katalogdan tohumlar. */
    public void ensureSeeded(long careerId) {
        Boolean any = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM career_player WHERE career_id = :c)", Map.of("c", careerId), Boolean.class);
        if (!Boolean.TRUE.equals(any)) {
            // Okuma yolları salt-okunur işlem içinde olabilir: tohumlama kendi işleminde yazılır (career satırına dokunmaz, kilit çakışması yok).
            new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW))
                    .executeWithoutResult((status) -> syncCareer(careerId, false));
        }
    }

    /** Kariyer silinirken: kariyerin oyuncu verisi ve kariyerde üretilmiş (owner_career_id) oyuncular. */
    public void deleteCareerPlayers(long careerId) {
        Map<String, Object> params = Map.of("c", careerId);
        jdbc.update("DELETE FROM career_player WHERE career_id = :c", params);
        jdbc.update("DELETE FROM career_player_data WHERE career_id = :c", params);
        jdbc.update("DELETE FROM player_valuation WHERE base_player_id IN (SELECT id FROM base_player WHERE owner_career_id = :c)", params);
        jdbc.update("DELETE FROM player_rating WHERE base_player_id IN (SELECT id FROM base_player WHERE owner_career_id = :c)", params);
        jdbc.update("DELETE FROM base_player WHERE owner_career_id = :c", params);
    }

    /** LE'ye özgü alanlar: role1..9 (aşinalık) ve trait2 gizli özellikleri + bilinmeyen bitler. Yalnız satırı olan oyuncular güncellenir. */
    public int applyLeExtras(long careerId, List<Map<String, String>> players, Map<Long, Long> eaToBase) {
        List<SqlParameterSource> updates = new ArrayList<>();
        for (Map<String, String> row : players) {
            Long baseId = eaToBase.get(toLong(row.get("playerid")));
            if (Objects.isNull(baseId) || !hasLeExtras(row)) {
                continue;
            }
            LePlaystyleBits.Decoded decoded = playstyleBits.decode(toLong(row.get("trait1")), toLong(row.get("trait2")), toLong(row.get("icontrait1")), toLong(row.get("icontrait2")));
            List<String> traits = new ArrayList<>(decoded.hiddenTraits());
            traits.addAll(decoded.unknown());
            updates.add(new MapSqlParameterSource().addValue("c", careerId).addValue("p", baseId)
                    .addValue("rf", familiarityJson(row)).addValue("ht", String.join(",", traits)));
        }
        if (updates.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate("UPDATE career_player SET role_familiarity = CAST(:rf AS jsonb), hidden_traits = string_to_array(NULLIF(:ht, ''), ',') WHERE career_id = :c AND player_id = :p",
                updates.toArray(new SqlParameterSource[0]));
        return updates.size();
    }

    /**
     * Sözleşme verisi (E3-6b): players.contractvaliduntil (yıl → o yılın 30 Haziran'ı, sezon sonu varsayımı), players.wage (ham LE değeri, birim doğrulanmadı) ve
     * playerloans.loandateend (1582-10-14'ten gün sayısı). Alan yoksa sütun NULL kalır = "sözleşme bilinmiyor".
     */
    public int applyContracts(long careerId, List<Map<String, String>> players, List<Map<String, String>> loans, Map<Long, Long> eaToBase) {
        Map<Long, LocalDate> loanEnd = new HashMap<>();
        if (Objects.nonNull(loans)) {
            for (Map<String, String> row : loans) {
                Long days = toLong(row.get("loandateend"));
                Long playerId = toLong(row.get("playerid"));
                if (Objects.nonNull(days) && Objects.nonNull(playerId)) {
                    loanEnd.put(playerId, LE_EPOCH.plusDays(days));
                }
            }
        }
        List<SqlParameterSource> updates = new ArrayList<>();
        if (Objects.nonNull(players)) {
            for (Map<String, String> row : players) {
                Long eaId = toLong(row.get("playerid"));
                Long baseId = eaToBase.get(eaId);
                Long year = toLong(row.get("contractvaliduntil"));
                Long wage = toLong(row.get("wage"));
                if (Objects.isNull(baseId) || (Objects.isNull(year) && Objects.isNull(wage) && !loanEnd.containsKey(eaId))) {
                    continue;
                }
                updates.add(new MapSqlParameterSource().addValue("c", careerId).addValue("p", baseId)
                        .addValue("until", Objects.isNull(year) || year < MIN_YEAR || year > MAX_YEAR ? null : java.sql.Date.valueOf(LocalDate.of(year.intValue(), SEASON_END_MONTH, SEASON_END_DAY)), java.sql.Types.DATE)
                        .addValue("wage", wage, java.sql.Types.BIGINT)
                        .addValue("loan", loanEnd.containsKey(eaId) ? java.sql.Date.valueOf(loanEnd.get(eaId)) : null, java.sql.Types.DATE));
            }
        }
        if (updates.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate("UPDATE career_player SET contract_until = :until, wage_eur = :wage, loan_until = :loan WHERE career_id = :c AND player_id = :p", updates.toArray(new SqlParameterSource[0]));
        return updates.size();
    }

    private static boolean hasLeExtras(Map<String, String> row) {
        return row.containsKey("trait2") || row.containsKey("role1");
    }

    /** {"41": 2}: kod % 100 = rol, kod >= 100 → Role++ (2), değilse Role+ (1). */
    private String familiarityJson(Map<String, String> row) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 1; i <= 9; i++) {
            Long code = toLong(row.get("role" + i));
            if (Objects.nonNull(code) && code > 0) {
                out.merge(String.valueOf(code % 100), code >= 100 ? 2 : 1, Math::max);
            }
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("role_familiarity", e);
        }
    }

    private void bumpVersion(long careerId) {
        jdbc.update("UPDATE career SET data_version = data_version + 1 WHERE id = :c", Map.of("c", careerId));
    }

    private static Long toLong(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
