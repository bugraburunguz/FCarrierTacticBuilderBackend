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
        properties.getRateLimit().setSolverPerMinute(2);
        properties.getRateLimit().setAnalysisPerMinute(5);
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

    /** V3-07a: şifre değiştir de şifre deneme yüzeyi — auth kovası ve Redis yokken fail-closed. */
    @Test
    void passwordChangeUsesTheAuthBucketAndFailsClosed() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(1L);
        run("/api/v1/me/password", "10.0.0.9");
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("auth:"), eq(Duration.ofMinutes(1)));

        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(run("/api/v1/me/password", "10.0.0.9").getStatus()).isEqualTo(429);
    }

    @Test
    void generalApiFailsOpenWhenRedisIsDown() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(run("/api/v1/players", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    /** E3-7b: /tactics/analysis ücretsiz ama sınırlıdır: kendi kovası (analysis:) ve dakikada analysisPerMinute; Redis yoksa açık geçer (pahalı değil, kimlik değil). */
    @Test
    void theFreeTacticAnalysisHasItsOwnBucketAndIsLimited() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(5L);
        assertThat(run("/api/v1/tactics/analysis", "10.0.0.1").getStatus()).isEqualTo(200);
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("analysis:"), eq(Duration.ofMinutes(1)));

        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(6L);
        assertThat(run("/api/v1/tactics/analysis", "10.0.0.1").getStatus()).isEqualTo(429);

        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(run("/api/v1/tactics/analysis", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void solverHasItsOwnBucketAndFailsClosed() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(3L);
        assertThat(run("/api/v1/ut/sbc/streamlined", "10.0.0.1").getStatus()).isEqualTo(429);
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("solver:"), eq(Duration.ofMinutes(1)));
        assertThat(run("/api/v1/players", "10.0.0.1").getStatus()).isEqualTo(200);

        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(run("/api/v1/ut/sbc/streamlined", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    /** PROD-PREP 6a: sahte X-Forwarded-For (güven açık ya da kapalı) ve güven kapalıyken sahte X-Real-IP ile auth sınırı aşılamaz. */
    @Test
    void spoofedForwardingHeadersCannotBypassTheLimit() throws Exception {
        for (boolean trust : new boolean[] {false, true}) {
            java.util.Map<String, Long> counts = new java.util.HashMap<>();
            when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenAnswer(call -> counts.merge(call.getArgument(0), 1L, Long::sum));
            filter = filter(trust);
            for (int i = 1; i <= 3; i++) {
                assertThat(run("/api/v1/auth/login", "10.0.0.1", "X-Forwarded-For", "198.51.100." + i).getStatus()).isEqualTo(200);
            }
            assertThat(run("/api/v1/auth/login", "10.0.0.1", "X-Forwarded-For", "198.51.100.99").getStatus()).as("trust=" + trust).isEqualTo(429);
            if (!trust) {
                assertThat(run("/api/v1/auth/login", "10.0.0.1", "X-Real-IP", "198.51.100.77").getStatus()).isEqualTo(429);
            }
        }
    }

    /** PROD-PREP 6a: güven açıkken nginx'in ilettiği X-Real-IP istemcileri ayırır (Railway edge → nginx → backend). */
    @Test
    void trustedRealIpSeparatesClientsBehindTheProxy() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(1L);
        filter = filter(true);
        run("/api/v1/auth/login", "10.0.0.1", "X-Real-IP", "203.0.113.5");
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("ip203.0.113.5"), eq(Duration.ofMinutes(1)));
    }

    /** PROD-PREP 4: hesap silme de şifre deneme yüzeyi — auth kovası. */
    @Test
    void accountDeletionUsesTheAuthBucket() throws Exception {
        when(counter.increment(anyString(), eq(Duration.ofMinutes(1)))).thenReturn(1L);
        run("/api/v1/me/delete", "10.0.0.9");
        verify(counter).increment(org.mockito.ArgumentMatchers.contains("auth:"), eq(Duration.ofMinutes(1)));
    }

    private RateLimitFilter filter(boolean trustProxy) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        SecurityProperties properties = new SecurityProperties();
        properties.getRateLimit().setAuthPerMinute(3);
        properties.setTrustProxy(trustProxy);
        return new RateLimitFilter(properties, counter, new MessageService(messages), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    private MockHttpServletResponse run(String path, String remote, String header, String value) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remote);
        request.addHeader(header, value);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
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
