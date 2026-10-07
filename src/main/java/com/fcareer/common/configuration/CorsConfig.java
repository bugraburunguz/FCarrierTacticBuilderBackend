package com.fcareer.common.configuration;

import com.fcareer.auth.configuration.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private static final List<String> METHODS = List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> HEADERS = List.of("Authorization", "Content-Type", "Accept");
    private static final long MAX_AGE_SECONDS = 3600;

    private final SecurityProperties securityProperties;

    /** Yalnızca tanımlı kaynaklara (ör. GitHub Pages) izin verir; liste boşsa hiçbir çapraz kaynak isteğine izin verilmez. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = securityProperties.getCorsAllowedOrigins().stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(origins);
            configuration.setAllowedMethods(METHODS);
            configuration.setAllowedHeaders(HEADERS);
            configuration.setAllowCredentials(false);
            configuration.setMaxAge(MAX_AGE_SECONDS);
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }
}
