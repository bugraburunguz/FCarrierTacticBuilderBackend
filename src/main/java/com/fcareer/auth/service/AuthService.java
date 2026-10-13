package com.fcareer.auth.service;

import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.model.SubscriptionType;
import com.fcareer.auth.model.request.LoginRequest;
import com.fcareer.auth.model.request.RefreshRequest;
import com.fcareer.auth.model.request.RegisterRequest;
import com.fcareer.auth.model.response.MeResponse;
import com.fcareer.auth.model.response.TokenResponse;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.auth.security.JwtService;
import com.fcareer.auth.security.RefreshTokenStore;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.service.CreditService;
import com.fcareer.common.exception.EmailAlreadyRegisteredException;
import com.fcareer.common.exception.InvalidCredentialsException;
import com.fcareer.common.exception.InvalidRefreshTokenException;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER = "Bearer";
    private static final String USER = "user";
    private static final int REFRESH_TOKEN_BYTES = 48;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final com.fcareer.ut.kb.LinkTokenStore linkTokenStore;
    private final SecurityProperties securityProperties;
    private final BillingProperties billingProperties;
    private final CreditService creditService;
    private final RegistrationLimiter registrationLimiter;
    private final EmailVerificationService emailVerification;
    private final BootstrapAdmin bootstrapAdmin;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile String dummyHash;

    private String dummyHash() {
        String hash = dummyHash;
        if (Objects.isNull(hash)) {
            hash = passwordEncoder.encode("fcareer-dummy-password");
            dummyHash = hash;
        }
        return hash;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request, String clientIp) {
        registrationLimiter.check(clientIp);
        String email = normalize(request.getEmail());
        if (appUserRepository.findByEmail(email).isPresent()) {
            log.warn("Registration rejected, email exists");
            throw new EmailAlreadyRegisteredException();
        }
        AppUserEntity user = new AppUserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setSubscriptionType(SubscriptionType.FREE);
        user.setDailyCreditLimit(billingProperties.getDailyCredits());
        user.setEmailVerified(!emailVerification.required());
        if (bootstrapAdmin.matches(email)) {
            user.setRole(AuthUser.ROLE_ADMIN);
            log.info("bootstrap admin atandı");
        }
        creditService.initialize(user);
        try {
            user = appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration rejected, concurrent duplicate email");
            throw new EmailAlreadyRegisteredException();
        }
        log.info("User registered userId : {}", user.getId());
        if (!user.isEmailVerified()) {
            emailVerification.issue(user);
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        java.util.Optional<AppUserEntity> found = appUserRepository.findByEmail(normalize(request.getEmail()));
        // Kullanıcı yoksa da bcrypt çalışır: yanıt süresi e-posta varlığını sızdırmaz (SEC-08).
        boolean matches = passwordEncoder.matches(request.getPassword(), found.map(AppUserEntity::getPasswordHash).orElse(dummyHash()));
        AppUserEntity user = found.filter(candidate -> matches).orElseThrow(() -> {
            log.warn("Login rejected");
            return new InvalidCredentialsException();
        });
        log.info("User logged in userId : {}", user.getId());
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        String tokenHash = hash(request.getRefreshToken());
        Long userId = refreshTokenStore.consume(tokenHash)
                .orElseThrow(() -> {
                    refreshTokenStore.usedBy(tokenHash).ifPresent(owner -> {
                        log.warn("Refresh token reuse detected, revoking all sessions userId : {}", owner);
                        refreshTokenStore.revokeAll(owner);
                    });
                    log.warn("Refresh rejected, unknown or expired token");
                    return new InvalidRefreshTokenException();
                });
        AppUserEntity user = appUserRepository.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        refreshTokenStore.delete(hash(request.getRefreshToken())).ifPresent(linkTokenStore::revokeAll);
    }

    @Transactional
    public MeResponse me(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException(USER, userId));
        CreditStatus status = creditService.status(userId);
        return MeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .subscriptionType(status.premium() ? SubscriptionType.PREMIUM : SubscriptionType.FREE)
                .creditBalance(status.premium() ? null : status.balance())
                .dailyCreditLimit(status.dailyLimit())
                .showAds(!status.premium())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .emailVerificationRequired(emailVerification.required())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private TokenResponse issueTokens(AppUserEntity user) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenStore.save(hash(refreshToken), user.getId(), securityProperties.getJwt().getRefreshTtl());
        return TokenResponse.builder()
                .accessToken(jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRole()))
                .refreshToken(refreshToken)
                .tokenType(BEARER)
                .expiresInSeconds(jwtService.accessTtlSeconds())
                .build();
    }

    private static String normalize(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
