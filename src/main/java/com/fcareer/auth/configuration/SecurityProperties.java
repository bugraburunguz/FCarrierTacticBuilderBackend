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
        private int apiPerMinute = 120;
        private int solverPerMinute = 10;
    }
}
