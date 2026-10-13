package com.fcareer.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Kayıt ve e-posta doğrulama ayarları (BE-SEC-06). */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.auth")
public class AuthProperties {

    private Registration registration = new Registration();
    private EmailVerification emailVerification = new EmailVerification();

    /** IP başına günlük kayıt sınırı; 0 kapatır. */
    @Getter
    @Setter
    public static class Registration {
        private int dailyPerIp = 5;
    }

    /** enabled null ise SMTP (spring.mail.host) tanımlıysa açılır; kapalıyken kayıt ve kredi bugünkü gibi çalışır. */
    @Getter
    @Setter
    public static class EmailVerification {
        private Boolean enabled;
        private Duration tokenTtl = Duration.ofHours(24);
        private Duration resendCooldown = Duration.ofSeconds(60);
        private String webBaseUrl = "http://localhost:8081";
    }
}
