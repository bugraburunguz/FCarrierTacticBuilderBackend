package com.fcareer.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.common.model.ApiResponse;
import com.fcareer.common.redis.RedisCounter;
import com.fcareer.common.service.MessageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import static com.fcareer.common.constants.ApiConstants.API_V1;
import static com.fcareer.common.constants.ErrorCodes.RATE_LIMIT_EXCEEDED;
import static com.fcareer.common.constants.RedisConstants.RATE_LIMIT_PREFIX;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = API_V1 + "/auth";
    private static final String SOLVER_PATH = API_V1 + "/ut/sbc/streamlined";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final SecurityProperties properties;
    private final RedisCounter counter;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRateLimit().isEnabled() || !request.getRequestURI().startsWith(API_V1);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authPath = request.getRequestURI().startsWith(AUTH_PATH);
        boolean solverPath = request.getRequestURI().startsWith(SOLVER_PATH);
        String bucket = authPath ? "auth:" : solverPath ? "solver:" : "api:";
        int limit = authPath ? properties.getRateLimit().getAuthPerMinute() : solverPath ? properties.getRateLimit().getSolverPerMinute() : properties.getRateLimit().getApiPerMinute();
        String key = RATE_LIMIT_PREFIX + bucket + subject(request) + ":" + minuteBucket();
        if (isOverLimit(key, limit, authPath || solverPath)) {
            log.warn("Rate limit exceeded path : {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(messageService.getMessage(RATE_LIMIT_EXCEEDED), RATE_LIMIT_EXCEEDED));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Redis yoksa genel API açık geçer (fail-open); kimlik ve pahalı çözücü uçları kapalı kalır (fail-closed) ki brute-force sınırı atlanamasın. */
    private boolean isOverLimit(String key, int limit, boolean failClosed) {
        try {
            return counter.increment(key, WINDOW) > limit;
        } catch (DataAccessException e) {
            log.warn("Rate limit store unavailable failClosed : {} reason : {}", failClosed, e.getMessage());
            return failClosed;
        }
    }

    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.nonNull(authentication) && authentication.getPrincipal() instanceof AuthUser user) {
            return "u" + user.id();
        }
        return "ip" + request.getRemoteAddr();
    }

    private long minuteBucket() {
        return clock.millis() / WINDOW.toMillis();
    }
}
