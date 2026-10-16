package com.fcareer.hardening;

import com.fcareer.common.configuration.ProdConfigGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PROD-PREP 6c/6e: prod profili eksik ya da tehlikeli ayarla açılmaz (e-posta sağlayıcısı, https web adresi, sahte ödeme kapalı). */
class ProdConfigGuardTest {

    private static MockEnvironment valid() {
        return new MockEnvironment()
                .withProperty("spring.mail.host", "smtp-relay.example.test")
                .withProperty("spring.mail.username", "user")
                .withProperty("spring.mail.password", "secret")
                .withProperty("fcareer.mail.from", "noreply@example.test")
                .withProperty("fcareer.auth.email-verification.web-base-url", "https://fc.example.test")
                .withProperty("fcareer.billing.stub-enabled", "false");
    }

    @Test
    void aCompleteConfigurationStarts() {
        assertThatCode(() -> new ProdConfigGuard(valid()).validate()).doesNotThrowAnyException();
    }

    @Test
    void missingOrUnsafeValuesStopStartup() {
        assertThatThrownBy(() -> new ProdConfigGuard(valid().withProperty("spring.mail.host", "")).validate()).hasMessageContaining("MAIL_HOST");
        assertThatThrownBy(() -> new ProdConfigGuard(valid().withProperty("spring.mail.password", "")).validate()).hasMessageContaining("MAIL_PASSWORD");
        assertThatThrownBy(() -> new ProdConfigGuard(valid().withProperty("fcareer.mail.from", "no-reply@localhost")).validate()).hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> new ProdConfigGuard(valid().withProperty("fcareer.auth.email-verification.web-base-url", "http://localhost:8081")).validate())
                .hasMessageContaining("WEB_BASE_URL");
        assertThatThrownBy(() -> new ProdConfigGuard(valid().withProperty("fcareer.billing.stub-enabled", "true")).validate()).hasMessageContaining("BILLING_STUB_ENABLED");
    }
}
