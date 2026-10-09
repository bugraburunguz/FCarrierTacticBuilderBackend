package com.fcareer.ut.kb;

import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.exception.PremiumRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Katkıcı bayrağı (08 D-20): admin verir/alır, her değişiklik denetim tablosuna yazılır; aktif katkıcı PREMIUM haklarını alır. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributorService {

    public static final int TRUSTED_THRESHOLD = 5;

    public record Contributor(long id, long userId, int trust, boolean banned) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<Contributor> find(Long userId) {
        List<Contributor> found = jdbc.query("SELECT id, user_id, trust, banned FROM contributor WHERE user_id = :u",
                Map.of("u", userId), (rs, i) -> new Contributor(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("trust"), rs.getBoolean("banned")));
        return found.stream().findFirst();
    }

    public boolean isActive(Long userId) {
        return find(userId).filter(c -> !c.banned()).isPresent();
    }

    public Contributor require(Long userId) {
        return find(userId).filter(c -> !c.banned()).orElseThrow(PremiumRequiredException::new);
    }

    @Transactional
    public void grant(long userId, String actor) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = :u", Map.of("u", userId), Integer.class);
        if (exists == null || exists == 0) {
            throw new NotFoundException("user", userId);
        }
        jdbc.update("INSERT INTO contributor (user_id, trust, banned) VALUES (:u, :t, false) ON CONFLICT (user_id) DO UPDATE SET banned = false",
                new MapSqlParameterSource().addValue("u", userId).addValue("t", TRUSTED_THRESHOLD));
        jdbc.update("INSERT INTO contributor_audit (user_id, action, actor) VALUES (:u, 'GRANTED', :a)", Map.of("u", userId, "a", actor));
        log.info("Contributor granted userId : {} actor : {}", userId, actor);
    }

    @Transactional
    public void revoke(long userId, String actor) {
        jdbc.update("UPDATE contributor SET banned = true WHERE user_id = :u", Map.of("u", userId));
        jdbc.update("INSERT INTO contributor_audit (user_id, action, actor) VALUES (:u, 'REVOKED', :a)", Map.of("u", userId, "a", actor));
        log.info("Contributor revoked userId : {} actor : {}", userId, actor);
    }

    @Transactional
    public void adjustTrust(long contributorId, int delta) {
        jdbc.update("UPDATE contributor SET trust = trust + :d WHERE id = :id", Map.of("d", delta, "id", contributorId));
    }
}
