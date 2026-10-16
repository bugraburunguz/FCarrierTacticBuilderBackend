package com.fcareer.ingestion.security;

import com.fcareer.auth.security.ClientIp;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

/**
 * /admin altında kimlik: ADMIN rolü JWT'den gelir (BE-SEC-07). `X-Admin-Key` yalnız makineden makineye uçlarda (ingestion, ml) kabul edilir;
 * kilit deposu erişilemezse reddedilir (fail-closed). Anahtar yoksa istek rol denetimine devredilir (anonim 401, USER 403).
 */
@Slf4j
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String FAIL_PREFIX = "fc:admin:fail:";
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 10;
    private static final List<String> MACHINE_PATHS = List.of(ADMIN_PATH + "/ingestion", ADMIN_PATH + "/ml");

    private final IngestionProperties properties;
    private final RedisCounter counter;
    /** PROD-PREP 6a: deneme sayacı anahtarı için istemci IP'si (ClientIp). */
    private final boolean trustProxy;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(ADMIN_KEY_HEADER);
        if (Objects.isNull(supplied) || isAdminAlready()) {
            chain.doFilter(request, response);
            return;
        }
        if (!machinePath(request.getRequestURI())) {
            log.warn("Admin key refused outside machine paths path : {}", request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        String failKey = FAIL_PREFIX + ClientIp.of(request, trustProxy);
        if (lockedOut(failKey)) {
            log.warn("Admin key locked out path : {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        String configured = properties.getAdminKey();
        if (configured.isBlank() || !constantTimeEquals(configured, supplied)) {
            log.warn("Admin key rejected path : {}", request.getRequestURI());
            registerFailure(failKey);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "machine", null, AuthorityUtils.createAuthorityList(ROLE_ADMIN)));
        chain.doFilter(request, response);
    }

    private static boolean machinePath(String uri) {
        return MACHINE_PATHS.stream().anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    private static boolean isAdminAlready() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return Objects.nonNull(authentication) && authentication.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    /** Kilit deposu erişilemezse anahtar denemesi reddedilir: brute-force sınırı atlanamaz. */
    private boolean lockedOut(String failKey) {
        try {
            return counter.current(failKey) >= MAX_FAILURES;
        } catch (DataAccessException e) {
            log.warn("Admin lockout store unavailable, rejecting key reason : {}", e.getMessage());
            return true;
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
