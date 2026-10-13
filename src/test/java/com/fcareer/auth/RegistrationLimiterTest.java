package com.fcareer.auth;

import com.fcareer.auth.configuration.AuthProperties;
import com.fcareer.auth.service.RegistrationLimiter;
import com.fcareer.common.exception.RateLimitExceededException;
import com.fcareer.common.redis.RedisCounter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

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

    private final RedisCounter counter = mock(RedisCounter.class);
    private final AuthProperties properties = new AuthProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsUpToTheDailyLimitThenRejects() {
        properties.getRegistration().setDailyPerIp(5);
        RegistrationLimiter limiter = new RegistrationLimiter(properties, counter, clock);
        when(counter.increment(anyString(), eq(Duration.ofHours(24)))).thenReturn(5L);
        assertThatCode(() -> limiter.check("10.0.0.1")).doesNotThrowAnyException();

        when(counter.increment(anyString(), eq(Duration.ofHours(24)))).thenReturn(6L);
        assertThatThrownBy(() -> limiter.check("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void zeroDisablesTheLimitAndAnUnreachableStoreFailsClosed() {
        properties.getRegistration().setDailyPerIp(0);
        new RegistrationLimiter(properties, counter, clock).check("10.0.0.1");
        verifyNoInteractions(counter);

        properties.getRegistration().setDailyPerIp(5);
        when(counter.increment(anyString(), eq(Duration.ofHours(24)))).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> new RegistrationLimiter(properties, counter, clock).check("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);
    }
}
