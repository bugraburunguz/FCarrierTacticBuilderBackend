package com.fcareer.ut.kb;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** Eklenti bağlama oturumları Postgres'te (OPS-02, Redis kaldırıldı). Token düz metin saklanmaz (SHA-256); süresi dolanlar yeni oturumda temizlenir. */
@Component
@RequiredArgsConstructor
public class JdbcLinkTokenStore implements LinkTokenStore {

    private static final int TOKEN_BYTES = 32;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String issue(Long userId, String mode, Duration ttl) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update("DELETE FROM ut_link_token WHERE expires_at <= :now", Map.of("now", Timestamp.from(now)));
        jdbc.update("INSERT INTO ut_link_token (token_hash, user_id, mode, expires_at) VALUES (:h, :u, :m, :exp)",
                new MapSqlParameterSource().addValue("h", hash(token)).addValue("u", userId).addValue("m", mode)
                        .addValue("exp", Timestamp.from(now.plus(ttl))));
        return token;
    }

    @Override
    public Optional<LinkGrant> resolve(String token) {
        return jdbc.query("SELECT user_id, mode FROM ut_link_token WHERE token_hash = :h AND expires_at > :now",
                new MapSqlParameterSource().addValue("h", hash(token)).addValue("now", Timestamp.from(clock.instant())),
                (rs, i) -> new LinkGrant(rs.getLong("user_id"), rs.getString("mode"))).stream().findFirst();
    }

    @Override
    public void revokeAll(Long userId) {
        jdbc.update("DELETE FROM ut_link_token WHERE user_id = :u", Map.of("u", userId));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
