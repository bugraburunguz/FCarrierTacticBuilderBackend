package com.fcareer.common.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.configuration.SecurityProperties;
import com.fcareer.auth.security.JwtAuthenticationFilter;
import com.fcareer.auth.security.JwtService;
import com.fcareer.auth.security.RateLimitFilter;
import com.fcareer.auth.security.SecurityErrorHandler;
import com.fcareer.common.service.MessageService;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.security.AdminKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fcareer.common.redis.RedisCounter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;
import static com.fcareer.common.constants.ApiConstants.API_V1;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_GET = {
            API_V1 + "/players/**", API_V1 + "/clubs", API_V1 + "/leagues", API_V1 + "/nationalities", API_V1 + "/roles", API_V1 + "/behavior-tags", API_V1 + "/formations",
            API_V1 + "/presets/**", API_V1 + "/config/**", API_V1 + "/wonderkids", API_V1 + "/ut/meta/**", API_V1 + "/ut/sbc/score-table", API_V1 + "/ut/card/*/upgrades", API_V1 + "/img/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    private static final String[] PUBLIC_POST = {
            API_V1 + "/auth/**", API_V1 + "/tactics/resolve", API_V1 + "/fit/role", API_V1 + "/players/compare",
            API_V1 + "/recommend/preview", API_V1 + "/tactics/export-code", API_V1 + "/tactics/import-code", API_V1 + "/players/*/development", API_V1 + "/ut/card/*/chemstyle", API_V1 + "/ut/sbc/streamlined", API_V1 + "/ut/squad/evaluate"};

    private final SecurityErrorHandler securityErrorHandler;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final IngestionProperties ingestionProperties;
    private final RedisCounter redisCounter;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(securityProperties, redisCounter, messageService,
                objectMapper, clock);
        AdminKeyFilter adminKeyFilter = new AdminKeyFilter(ingestionProperties, redisCounter);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminKeyFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, AdminKeyFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ADMIN_PATH + "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
