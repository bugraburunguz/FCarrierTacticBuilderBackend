package com.fcareer.common.configuration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Çözümlemeden önce Content-Length ile reddeder; /ut/capture gibi büyük JSON alan uçlar için bellek koruması (SEC-05). */
@Configuration
public class RequestSizeLimitConfig {

    static final long CAPTURE_MAX_BYTES = 60L * 1024 * 1024;

    @Bean
    public FilterRegistrationBean<jakarta.servlet.Filter> captureSizeLimit() {
        FilterRegistrationBean<jakarta.servlet.Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            if (request.getContentLengthLong() > CAPTURE_MAX_BYTES && response instanceof HttpServletResponse http) {
                http.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                http.setContentType("application/json");
                http.getWriter().write("{\"success\":false,\"message\":\"Payload too large\"}");
                return;
            }
            chain.doFilter(request, response);
        });
        registration.addUrlPatterns(API_V1 + "/ut/capture");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
