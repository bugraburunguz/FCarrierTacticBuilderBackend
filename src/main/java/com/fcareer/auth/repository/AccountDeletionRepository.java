package com.fcareer.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * PROD-PREP 4 (KVKK hesap silme): kariyer dışı kullanıcı verisi. Kariyerler CareerService.delete yolundan silinir (kariyer tabloları
 * career'a ON DELETE CASCADE ile bağlı). Katkılar silinmez: contrib_event / price_obs satırlarının katkıcı bağı NULL'a çekilir, katkıcı kaydı silinir.
 */
@Repository
@RequiredArgsConstructor
public class AccountDeletionRepository {

    private static final String USER = "u";

    private final NamedParameterJdbcTemplate jdbc;

    public List<Long> careerIds(long userId) {
        return jdbc.queryForList("SELECT id FROM career WHERE user_id = :u ORDER BY id", Map.of(USER, userId), Long.class);
    }

    public long adminCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE role = 'ADMIN'", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    /** Katkıları anonimleştirir ve katkıcı kaydını (denetim izi dahil) siler; anonimleşen katkı satırı sayısını döner. */
    public int anonymizeContributions(long userId) {
        Map<String, Object> params = Map.of(USER, userId);
        int events = jdbc.update("UPDATE contrib_event SET contributor_id = NULL WHERE contributor_id IN (SELECT id FROM contributor WHERE user_id = :u)", params);
        int prices = jdbc.update("UPDATE price_obs SET contributor_id = NULL WHERE contributor_id IN (SELECT id FROM contributor WHERE user_id = :u)", params);
        jdbc.update("DELETE FROM contributor WHERE user_id = :u", params);
        jdbc.update("DELETE FROM contributor_audit WHERE user_id = :u", params);
        return events + prices;
    }

    /** Kariyer dışı kişisel kayıtlar ve son olarak hesabın kendisi. */
    public void deleteUserRows(long userId) {
        Map<String, Object> params = Map.of(USER, userId);
        jdbc.update("UPDATE ea_id_suggestion SET voters = array_remove(voters, CAST(:u AS bigint)) WHERE CAST(:u AS bigint) = ANY(voters)", params);
        jdbc.update("DELETE FROM sbc_vote WHERE user_id = :u", params);
        jdbc.update("DELETE FROM user_tactic WHERE user_id = :u", params);
        jdbc.update("DELETE FROM player_valuation WHERE source = 'IMPORT' AND owner_user_id = :u", params);
        jdbc.update("DELETE FROM credit_transaction WHERE user_id = :u", params);
        jdbc.update("DELETE FROM subscription WHERE user_id = :u", params);
        jdbc.update("DELETE FROM email_verification_token WHERE user_id = :u", params);
        jdbc.update("DELETE FROM app_user WHERE id = :u", params);
    }
}
