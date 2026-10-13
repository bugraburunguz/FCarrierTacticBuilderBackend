package com.fcareer.auth.service;

import com.fcareer.auth.configuration.RateLimitProperties;
import com.fcareer.common.exception.RateLimitExceededException;
import com.fcareer.common.redis.RedisCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.fcareer.common.constants.RedisConstants.RATE_LIMIT_PREFIX;

/** IP başına günlük kayıt sınırı (BE-SEC-06): bedava hesap çiftliğini yavaşlatır. 0 kapatır; sayaç deposu yoksa kapalı kalır (fail-closed). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationLimiter {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final RateLimitProperties properties;
    private final RedisCounter counter;
    private final Clock clock;

    public void check(String ip) {
        int limit = properties.getRegister().getDailyPerIp();
        if (limit <= 0) {
            return;
        }
        String key = RATE_LIMIT_PREFIX + "register:" + ip + ":" + LocalDate.now(clock.withZone(ZoneOffset.UTC));
        try {
            if (counter.increment(key, WINDOW) > limit) {
                log.warn("Registration limit exceeded for ip");
                throw new RateLimitExceededException();
            }
        } catch (DataAccessException e) {
            log.warn("Registration limit store unavailable reason : {}", e.getMessage());
            throw new RateLimitExceededException();
        }
    }
}
