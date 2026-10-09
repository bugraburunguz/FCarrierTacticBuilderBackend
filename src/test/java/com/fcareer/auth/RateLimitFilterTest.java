package com.fcareer.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.auth.security.RateLimitFilter;
import com.fcareer.common.redis.RedisCounter;
import com.fcareer.common.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RedisCounter counter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        counter = mock(RedisCounter.class);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().setAuthPerMinute(3);
        filter = new RateLimitFilter(properties, counter, new MessageService(messages), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void blocksWhenCounterExceedsLimit() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(1L);
        assertThat(run("/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);

        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(4L);
        MockHttpServletResponse blocked = run("/api/v1/auth/login", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("fc.exception.0013");
    }

    @Test
    void separatesClientsByRemoteAddress() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(1L);
        run("/api/v1/auth/login", "10.0.0.1");
        run("/api/v1/auth/login", "10.0.0.2");

        verify(counter).increment(org.mockito.ArgumentMatchers.contains("ip10.0.0.1"), eq(Duration.ofMinutes(1)));
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("ip10.0.0.2"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void authPathsFailClosedWhenRedisIsDown() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(run("/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    void generalApiFailsOpenWhenRedisIsDown() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(run("/api/v1/players", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        assertThat(run("/actuator/health", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse run(String path, String remote) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remote);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
