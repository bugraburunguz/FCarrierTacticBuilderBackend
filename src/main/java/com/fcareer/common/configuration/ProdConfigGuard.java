package com.fcareer.common.configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PROD-PREP 6c/6e: prod profili eksik ya da tehlikeli ayarla açılmaz. E-posta doğrulaması prod'da zorunlu olduğundan SMTP (Brevo) değerleri,
 * doğrulama bağlantısı için https WEB_BASE_URL ve kapalı sahte ödeme şarttır. Hata mesajı yalnız değişken adlarını içerir (değer yok).
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdConfigGuard {

    private final Environment environment;

    @PostConstruct
    public void validate() {
        List<String> problems = new ArrayList<>();
        require(problems, "spring.mail.host", "MAIL_HOST");
        require(problems, "spring.mail.username", "MAIL_USERNAME");
        require(problems, "spring.mail.password", "MAIL_PASSWORD");
        String from = environment.getProperty("fcareer.mail.from", "");
        if (from.isBlank() || from.endsWith("@localhost")) {
            problems.add("MAIL_FROM");
        }
        if (!environment.getProperty("fcareer.auth.email-verification.web-base-url", "").startsWith("https://")) {
            problems.add("WEB_BASE_URL (https)");
        }
        if (environment.getProperty("fcareer.billing.stub-enabled", Boolean.class, false)) {
            problems.add("BILLING_STUB_ENABLED=false");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Prod yapılandırması eksik/hatalı: " + String.join(", ", problems));
        }
    }

    private void require(List<String> problems, String property, String variable) {
        if (environment.getProperty(property, "").isBlank()) {
            problems.add(variable);
        }
    }
}
