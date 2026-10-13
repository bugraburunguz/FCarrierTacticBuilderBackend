package com.fcareer.auth.security;

import com.fcareer.auth.configuration.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";

    private final SecurityProperties.Jwt config;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(SecurityProperties properties, Clock clock) {
        this.config = properties.getJwt();
        this.clock = clock;
        byte[] secret = config.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("fcareer.security.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String createAccessToken(Long userId, String email, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(config.getIssuer())
                .subject(String.valueOf(userId))
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.getAccessTtl())))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.getIssuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            return Optional.of(new AuthUser(Long.valueOf(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class), AuthUser.ROLE_ADMIN.equals(role) ? AuthUser.ROLE_ADMIN : AuthUser.ROLE_USER));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Access token rejected reason : {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return config.getAccessTtl().toSeconds();
    }
}
