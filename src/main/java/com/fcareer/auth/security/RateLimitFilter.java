package com.fcareer.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.common.model.ApiResponse;
import com.fcareer.common.limit.WindowCounter;
import com.fcareer.common.service.MessageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.fcareer.common.constants.ApiConstants.API_V1;
import static com.fcareer.common.constants.ErrorCodes.RATE_LIMIT_EXCEEDED;
import static com.fcareer.common.constants.KeyConstants.RATE_LIMIT_PREFIX;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = API_V1 + "/auth";
    private static final String PASSWORD_PATH = API_V1 + "/me/password";
    private static final String DELETE_PATH = API_V1 + "/me/delete";
    private static final String SOLVER_PATH = API_V1 + "/ut/sbc/streamlined";
    private static final String IMAGE_PATH = API_V1 + "/img/";
    /** GUV-2 (F8): pahalı ama ücretsiz uçlar analiz kovasında. */
    private static final Pattern ANALYSIS_PATHS = Pattern.compile(Pattern.quote(API_V1)
            + "/(tactics/analysis|fit/lineup|replicas/[^/]+/apply|recommend/preview|recommend/slot-view|careers/\\d+/starter-tactic)/?$");
    private static final Pattern IMPORT_PATH = Pattern.compile(Pattern.quote(API_V1) + "/careers/\\d+/import/?$");
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration IMPORT_WINDOW = Duration.ofHours(1);

    private final SecurityProperties properties;
    private final WindowCounter counter;
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
        // Şifre değiştir (V3-07a) ve hesap silme (PROD-PREP 4) şifre deneme yüzeyi: /auth ile aynı sıkı, fail-closed kova.
        boolean authPath = request.getRequestURI().startsWith(AUTH_PATH) || request.getRequestURI().equals(PASSWORD_PATH)
                || request.getRequestURI().equals(DELETE_PATH);
        boolean solverPath = request.getRequestURI().startsWith(SOLVER_PATH);
        boolean analysisPath = ANALYSIS_PATHS.matcher(request.getRequestURI()).matches();
        boolean importPath = IMPORT_PATH.matcher(request.getRequestURI()).matches();
        // Kart/yüz görselleri sayfa başına onlarca istek: genel API kovasını doldurmasın diye kendi (geniş) kovası.
        boolean imagePath = request.getRequestURI().startsWith(IMAGE_PATH);
        String bucket = authPath ? "auth:" : solverPath ? "solver:" : analysisPath ? "analysis:" : importPath ? "import:" : imagePath ? "img:" : "api:";
        int limit = authPath ? properties.getRateLimit().getAuthPerMinute() : solverPath ? properties.getRateLimit().getSolverPerMinute()
                : analysisPath ? properties.getRateLimit().getAnalysisPerMinute() : importPath ? properties.getRateLimit().getImportPerHour()
                : imagePath ? properties.getRateLimit().getImagePerMinute() : properties.getRateLimit().getApiPerMinute();
        Duration window = importPath ? IMPORT_WINDOW : WINDOW;
        String key = RATE_LIMIT_PREFIX + bucket + subject(request) + ":" + clock.millis() / window.toMillis();
        if (counter.increment(key, window) > limit) {
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

    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.nonNull(authentication) && authentication.getPrincipal() instanceof AuthUser user) {
            return "u" + user.id();
        }
        return "ip" + ClientIp.of(request, properties.isTrustProxy());
    }
}
