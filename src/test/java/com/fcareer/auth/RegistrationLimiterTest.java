package com.fcareer.auth;

import com.fcareer.auth.configuration.RateLimitProperties;
import com.fcareer.auth.service.RegistrationLimiter;
import com.fcareer.common.exception.RateLimitExceededException;
import com.fcareer.common.limit.WindowCounter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationLimiterTest {

    private final WindowCounter counter = mock(WindowCounter.class);
    private final RateLimitProperties properties = new RateLimitProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsUpToTheDailyLimitThenRejects() {
        properties.getRegister().setDailyPerIp(5);
        RegistrationLimiter limiter = new RegistrationLimiter(properties, counter, clock);
        when(counter.increment(anyString(), eq(Duration.ofHours(24)))).thenReturn(5L);
        assertThatCode(() -> limiter.check("10.0.0.1")).doesNotThrowAnyException();

        when(counter.increment(anyString(), eq(Duration.ofHours(24)))).thenReturn(6L);
        assertThatThrownBy(() -> limiter.check("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void zeroDisablesTheLimit() {
        properties.getRegister().setDailyPerIp(0);
        new RegistrationLimiter(properties, counter, clock).check("10.0.0.1");
        verifyNoInteractions(counter);
    }

    /** OPS-02: gerçek bellek içi sayaçla altıncı kayıt reddedilir. */
    @Test
    void theRealCounterRejectsTheSixthRegistrationOfTheDay() {
        properties.getRegister().setDailyPerIp(5);
        RegistrationLimiter limiter = new RegistrationLimiter(properties, new WindowCounter(clock), clock);
        for (int i = 0; i < 5; i++) {
            limiter.check("10.0.0.7");
        }
        assertThatThrownBy(() -> limiter.check("10.0.0.7")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.check("10.0.0.8")).doesNotThrowAnyException();
    }
}
