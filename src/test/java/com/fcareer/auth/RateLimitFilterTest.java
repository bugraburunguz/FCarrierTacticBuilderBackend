package com.fcareer.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.auth.security.RateLimitFilter;
import com.fcareer.common.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RateLimitFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().setAuthPerMinute(3);
        filter = new RateLimitFilter(properties, redis, new MessageService(messages), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void blocksWhenCounterExceedsLimitAndSetsWindowOnFirstHit() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse first = run("/api/v1/auth/login");
        assertThat(first.getStatus()).isEqualTo(200);
        verify(redis).expire(anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(1)));

        when(values.increment(anyString())).thenReturn(4L);
        MockHttpServletResponse blocked = run("/api/v1/auth/login");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("fc.exception.0013");
    }

    @Test
    void failsOpenWhenRedisIsDown() throws Exception {
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(run("/api/v1/auth/login").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        assertThat(run("/actuator/health").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse run(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
