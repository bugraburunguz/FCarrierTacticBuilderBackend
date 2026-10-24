package com.fcareer.career.service;

import com.fcareer.auth.configuration.RateLimitProperties;
import com.fcareer.career.repository.CareerRepository;
import com.fcareer.common.exception.CareerCreateLimitExceededException;
import com.fcareer.common.exception.CareerLimitReachedException;
import com.fcareer.common.limit.WindowCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.fcareer.common.constants.KeyConstants.RATE_LIMIT_PREFIX;

/**
 * GUV-2 (F4): her kariyer katalog kopyalar. Açık kariyer üst sınırı (422) ve günlük açma sınırı (429; sil-aç döngüsünü keser).
 * Günlük sayaç bellek içi, UTC gününe bağlı (yeniden başlatmada sıfırlanır, RegistrationLimiter ile aynı).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareerLimiter {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final RateLimitProperties properties;
    private final CareerRepository careerRepository;
    private final WindowCounter counter;
    private final Clock clock;

    public void checkCreate(Long userId) {
        int max = properties.getCareer().getMaxPerUser();
        if (max > 0 && careerRepository.countByUserId(userId) >= max) {
            throw new CareerLimitReachedException(max);
        }
        int daily = properties.getCareer().getDailyCreatePerUser();
        if (daily <= 0) {
            return;
        }
        String key = RATE_LIMIT_PREFIX + "career-create:" + userId + ":" + LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (counter.increment(key, WINDOW) > daily) {
            log.warn("Daily career create limit exceeded userId : {}", userId);
            throw new CareerCreateLimitExceededException(daily);
        }
    }
}
