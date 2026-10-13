package com.fcareer.auth.service;

import com.fcareer.auth.configuration.AuthProperties;
import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.mail.VerificationMailer;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.billing.service.CreditService;
import com.fcareer.common.exception.InvalidVerificationTokenException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * E-posta doğrulaması (BE-SEC-06). Açıksa yeni hesap 0 kredi ile başlar; bağlantı kullanılınca günlük kredi verilir.
 * Token yalnız hash'i saklanır, hiçbir profilde loglanmaz; yeni token eskisini geçersiz kılar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String USER = "user";
    private static final int TOKEN_BYTES = 32;

    private final AuthProperties properties;
    private final JdbcTemplate jdbc;
    private final AppUserRepository appUserRepository;
    private final CreditService creditService;
    private final VerificationMailer mailer;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.host:}")
    private String mailHost;

    /** Açık/kapalı: ayar verilmişse o, yoksa SMTP tanımlıysa açık. */
    public boolean required() {
        Boolean enabled = properties.getEmailVerification().getEnabled();
        return Objects.nonNull(enabled) ? enabled : !mailHost.isBlank();
    }

    /** Kayıt sonrası ya da yeniden gönderimde yeni token üretir ve gönderir. */
    @Transactional
    public void issue(AppUserEntity user) {
        jdbc.update("DELETE FROM email_verification_token WHERE user_id = ?", user.getId());
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update("INSERT INTO email_verification_token (token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)", hash(token), user.getId(),
                Timestamp.from(now.plus(properties.getEmailVerification().getTokenTtl())), Timestamp.from(now));
        String link = properties.getEmailVerification().getWebBaseUrl().replaceAll("/+$", "") + "/verify-email?token=" + token;
        mailer.sendVerification(user.getEmail(), link, token);
    }

    @Transactional
    public void verify(String token) {
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM email_verification_token WHERE token_hash = ? AND expires_at > ?", Long.class, hash(token),
                Timestamp.from(clock.instant()));
        if (owners.isEmpty()) {
            log.warn("Verification rejected, unknown or expired token");
            throw new InvalidVerificationTokenException();
        }
        Long userId = owners.get(0);
        jdbc.update("DELETE FROM email_verification_token WHERE user_id = ?", userId);
        AppUserEntity user = appUserRepository.findByIdForUpdate(userId).orElseThrow(InvalidVerificationTokenException::new);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            creditService.grantOnVerification(user);
            log.info("Email verified userId : {}", userId);
        }
    }

    /** Yeniden gönderim: bekleme süresi dolmadan 429; doğrulanmış hesapta işlem yapmaz. */
    @Transactional
    public void resend(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException(USER, userId));
        if (user.isEmailVerified() || !required()) {
            return;
        }
        List<Timestamp> last = jdbc.queryForList("SELECT created_at FROM email_verification_token WHERE user_id = ?", Timestamp.class, userId);
        if (!last.isEmpty() && last.get(0).toInstant().plus(properties.getEmailVerification().getResendCooldown()).isAfter(clock.instant())) {
            throw new RateLimitExceededException();
        }
        issue(user);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
