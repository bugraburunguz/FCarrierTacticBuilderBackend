package com.fcareer.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Yenileme token'ları Postgres'te (OPS-02, Redis kaldırıldı). Etkin token: used_at boş ve süresi dolmamış. Tüketim tek UPDATE ile atomik;
 * tüketilen token {@link #USED_MEMORY} boyunca "kullanıldı" hatırası olarak kalır (yeniden kullanım tespiti, SEC-08). Süresi dolan satırlar
 * yeni token kaydedilirken temizlenir.
 */
@Component
@RequiredArgsConstructor
public class JdbcRefreshTokenStore implements RefreshTokenStore {

    private static final Duration USED_MEMORY = Duration.ofDays(14);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public void save(String tokenHash, Long userId, Duration ttl) {
        Instant now = clock.instant();
        jdbc.update("DELETE FROM auth_refresh_token WHERE expires_at <= :now", Map.of("now", Timestamp.from(now)));
        jdbc.update("INSERT INTO auth_refresh_token (token_hash, user_id, expires_at) VALUES (:h, :u, :exp)",
                new MapSqlParameterSource().addValue("h", tokenHash).addValue("u", userId).addValue("exp", Timestamp.from(now.plus(ttl))));
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        Instant now = clock.instant();
        return jdbc.queryForList("""
                UPDATE auth_refresh_token SET used_at = :now, expires_at = :remember
                 WHERE token_hash = :h AND used_at IS NULL AND expires_at > :now RETURNING user_id""",
                new MapSqlParameterSource().addValue("h", tokenHash).addValue("now", Timestamp.from(now))
                        .addValue("remember", Timestamp.from(now.plus(USED_MEMORY))), Long.class).stream().findFirst();
    }

    @Override
    public Optional<Long> delete(String tokenHash) {
        return jdbc.queryForList("""
                DELETE FROM auth_refresh_token WHERE token_hash = :h AND used_at IS NULL AND expires_at > :now RETURNING user_id""",
                new MapSqlParameterSource().addValue("h", tokenHash).addValue("now", Timestamp.from(clock.instant())), Long.class).stream().findFirst();
    }

    @Override
    public Optional<Long> usedBy(String tokenHash) {
        return jdbc.queryForList("""
                SELECT user_id FROM auth_refresh_token WHERE token_hash = :h AND used_at IS NOT NULL AND expires_at > :now""",
                new MapSqlParameterSource().addValue("h", tokenHash).addValue("now", Timestamp.from(clock.instant())), Long.class).stream().findFirst();
    }

    @Override
    public void revokeAll(Long userId) {
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id = :u AND used_at IS NULL", Map.of("u", userId));
    }
}
