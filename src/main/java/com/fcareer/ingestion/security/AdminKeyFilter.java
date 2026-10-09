package com.fcareer.ingestion.security;

import com.fcareer.common.redis.RedisCounter;
import com.fcareer.ingestion.configuration.IngestionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

@Slf4j
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String FAIL_PREFIX = "fc:admin:fail:";
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 10;

    private final IngestionProperties properties;
    private final RedisCounter counter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.getAdminKey();
        String supplied = request.getHeader(ADMIN_KEY_HEADER);
        String failKey = FAIL_PREFIX + request.getRemoteAddr();
        if (lockedOut(failKey)) {
            log.warn("Admin key locked out path : {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        if (configured.isBlank() || Objects.isNull(supplied) || !constantTimeEquals(configured, supplied)) {
            log.warn("Admin key rejected path : {}", request.getRequestURI());
            registerFailure(failKey);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, AuthorityUtils.createAuthorityList(ROLE_ADMIN)));
        chain.doFilter(request, response);
    }

    private boolean lockedOut(String failKey) {
        try {
            return counter.current(failKey) >= MAX_FAILURES;
        } catch (DataAccessException e) {
            log.warn("Admin lockout store unavailable reason : {}", e.getMessage());
            return false;
        }
    }

    private void registerFailure(String failKey) {
        try {
            counter.increment(failKey, LOCKOUT);
        } catch (DataAccessException e) {
            log.warn("Admin failure not recorded reason : {}", e.getMessage());
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
