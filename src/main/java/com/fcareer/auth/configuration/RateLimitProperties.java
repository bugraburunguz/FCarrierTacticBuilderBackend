package com.fcareer.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hız sınırı ayarları (fcareer.ratelimit.*). Kayıt: IP başına günlük sınır; prod varsayılanı 5, dev profili gate betikleri için geniştir. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.ratelimit")
public class RateLimitProperties {

    private Register register = new Register();

    /** IP başına günlük kayıt sınırı; 0 kapatır. */
    @Getter
    @Setter
    public static class Register {
        private int dailyPerIp = 5;
    }
}
