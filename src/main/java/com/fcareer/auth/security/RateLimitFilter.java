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
import org.springframework.web.util.UrlPathHelper;

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
    /** GUV-2 (F8): pahalı ama ücretsiz uçlar analiz kovasında. Id parçası serbest: Spring "{id}"yi Long.decode ile okur ("+7", "0x7"). */
    private static final Pattern ANALYSIS_PATHS = Pattern.compile(Pattern.quote(API_V1)
            + "/(fit/lineup|replicas/[^/]+/apply|recommend/preview|recommend/slot-view|careers/[^/]+/starter-tactic|careers/[^/]+/development/recommendations|careers/[^/]+/academy)/?$");
    /** FIT-2 kararı: taktik analizi kendi kovasında (Lab'da diziliş isteğiyle birlikte gider; ortak kovayı yarı hızda doldurmasın). */
    private static final Pattern TACTIC_ANALYSIS_PATH = Pattern.compile(Pattern.quote(API_V1) + "/tactics/analysis/?$");
    private static final Pattern IMPORT_PATH = Pattern.compile(Pattern.quote(API_V1) + "/careers/[^/]+/import/?$");
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration IMPORT_WINDOW = Duration.ofHours(1);
    /** Kovalar Spring'in eşlediği gibi çözülmüş yol üzerinden seçilir; "%6Cineup" ya da "/api/%761" kovayı atlatamaz. */
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final SecurityProperties properties;
    private final WindowCounter counter;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRateLimit().isEnabled() || !path(request).startsWith(API_V1);
    }

    private static String path(HttpServletRequest request) {
        return PATHS.getPathWithinApplication(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Şifre değiştir (V3-07a) ve hesap silme (PROD-PREP 4) şifre deneme yüzeyi: /auth ile aynı sıkı, fail-closed kova.
        String path = path(request);
        boolean authPath = path.startsWith(AUTH_PATH) || path.equals(PASSWORD_PATH) || path.equals(DELETE_PATH);
        boolean solverPath = path.startsWith(SOLVER_PATH);
        boolean analysisPath = ANALYSIS_PATHS.matcher(path).matches();
        boolean tacticAnalysisPath = TACTIC_ANALYSIS_PATH.matcher(path).matches();
        boolean importPath = IMPORT_PATH.matcher(path).matches();
        // Kart/yüz görselleri sayfa başına onlarca istek: genel API kovasını doldurmasın diye kendi (geniş) kovası.
        boolean imagePath = path.startsWith(IMAGE_PATH);
        String bucket = authPath ? "auth:" : solverPath ? "solver:" : analysisPath ? "analysis:" : tacticAnalysisPath ? "tactic-analysis:"
                : importPath ? "import:" : imagePath ? "img:" : "api:";
        int limit = authPath ? properties.getRateLimit().getAuthPerMinute() : solverPath ? properties.getRateLimit().getSolverPerMinute()
                : analysisPath ? properties.getRateLimit().getAnalysisPerMinute()
                : tacticAnalysisPath ? properties.getRateLimit().getTacticAnalysisPerMinute() : importPath ? properties.getRateLimit().getImportPerHour()
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
