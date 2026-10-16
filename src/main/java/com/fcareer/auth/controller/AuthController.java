package com.fcareer.auth.controller;

import com.fcareer.auth.model.request.ChangePasswordRequest;
import com.fcareer.auth.model.request.LoginRequest;
import com.fcareer.auth.model.request.RefreshRequest;
import com.fcareer.auth.model.request.RegisterRequest;
import com.fcareer.auth.model.response.MeResponse;
import com.fcareer.auth.model.response.TokenResponse;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.auth.security.ClientIp;
import com.fcareer.auth.service.AccountDeletionService;
import com.fcareer.auth.service.AuthService;
import com.fcareer.auth.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final AccountDeletionService accountDeletionService;
    private final SecurityProperties securityProperties;

    public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {
    }

    @PostMapping("/auth/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, ClientIp.of(http, securityProperties.isTrustProxy()));
    }

    @PostMapping("/auth/verify-email")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }

    @PostMapping("/me/email/resend")
    public void resendVerification(@AuthenticationPrincipal AuthUser user) {
        emailVerificationService.resend(user.id());
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/auth/logout")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    /** V3-07a: şifre değiştir (oturum gerekir; /auth ile aynı sıkı hız sınırı). Diğer oturumlar kapanır, bu oturum için yeni token'lar döner. */
    @PostMapping("/me/password")
    public TokenResponse changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(user.id(), request);
    }

    public record DeleteAccountRequest(@NotBlank @Size(max = 72) String password, @NotBlank @Size(max = 254) String emailConfirm) {
    }

    /** PROD-PREP 4 (KVKK): hesabı ve kişisel veriyi kalıcı siler; bilgi tabanı katkıları anonimleşir. Mevcut şifre + e-posta onayı zorunlu. */
    @PostMapping("/me/delete")
    public void deleteAccount(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(user.id(), request.password(), request.emailConfirm());
    }

    public record PrivacyAckRequest(@NotNull @Min(1) Integer version) {
    }

    /** PROD-PREP 3: aydınlatma metni bilgilendirme bandı onayı (yeni sürüm ya da önceden onayı olmayan hesap). */
    @PostMapping("/me/privacy-ack")
    public void acknowledgePrivacy(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PrivacyAckRequest request) {
        authService.acknowledgePrivacy(user.id(), request.version());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }
}
