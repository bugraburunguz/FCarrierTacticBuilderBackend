package com.fcareer.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();
    private int minPasswordLength = 8;
    private List<String> corsAllowedOrigins = new ArrayList<>();
    /** PROD-PREP 6a: true iken istemci IP'si güvenilen X-Real-IP başlığından (ClientIp); yalnız ters vekil arkasında açılır. */
    private boolean trustProxy;

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private String issuer = "fcareer";
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(14);
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int authPerMinute = 10;
        private int apiPerMinute = 300;
        /** Kart/yüz görselleri (/img/**): sayfa başına onlarca istek; kendi kovası. */
        private int imagePerMinute = 1200;
        private int solverPerMinute = 10;
        /** Ücretsiz taktik analizi (/tactics/analysis): dakikada istek sayısı. */
        private int analysisPerMinute = 30;
    }
}
