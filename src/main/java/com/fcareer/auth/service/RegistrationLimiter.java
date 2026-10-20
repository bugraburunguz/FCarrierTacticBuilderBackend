package com.fcareer.auth.service;

import com.fcareer.auth.configuration.RateLimitProperties;
import com.fcareer.common.exception.RateLimitExceededException;
import com.fcareer.common.limit.WindowCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.fcareer.common.constants.KeyConstants.RATE_LIMIT_PREFIX;

/** IP başına günlük kayıt sınırı (BE-SEC-06): bedava hesap çiftliğini yavaşlatır. 0 kapatır. Sayaç bellek içi (yeniden başlatmada sıfırlanır). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationLimiter {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final RateLimitProperties properties;
    private final WindowCounter counter;
    private final Clock clock;

    public void check(String ip) {
        int limit = properties.getRegister().getDailyPerIp();
        if (limit <= 0) {
            return;
        }
        String key = RATE_LIMIT_PREFIX + "register:" + ip + ":" + LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (counter.increment(key, WINDOW) > limit) {
            log.warn("Registration limit exceeded for ip");
            throw new RateLimitExceededException();
        }
    }
}
