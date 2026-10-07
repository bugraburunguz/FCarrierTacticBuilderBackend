package com.fcareer.common;

import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.common.configuration.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private static CorsConfiguration resolve(List<String> origins, String path) {
        SecurityProperties properties = new SecurityProperties();
        properties.setCorsAllowedOrigins(origins);
        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return source.getCorsConfiguration(request);
    }

    @Test
    void allowsOnlyConfiguredOriginsOnApiPaths() {
        CorsConfiguration configuration = resolve(List.of("https://bugraburunguz.github.io"), "/api/v1/players");
        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://bugraburunguz.github.io")).isEqualTo("https://bugraburunguz.github.io");
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getAllowedHeaders()).contains("Authorization");
    }

    @Test
    void doesNotApplyOutsideApiPaths() {
        assertThat(resolve(List.of("https://bugraburunguz.github.io"), "/actuator/health")).isNull();
    }

    @Test
    void emptyListKeepsTheCurrentNoCrossOriginBehaviour() {
        assertThat(resolve(List.of(), "/api/v1/players")).isNull();
        assertThat(resolve(List.of("  "), "/api/v1/players")).isNull();
    }
}
