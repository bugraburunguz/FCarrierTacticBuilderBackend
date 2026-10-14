package com.fcareer.recommendation.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/**
 * E3-10: web `POST /careers/{id}/recommendations` ucuna geçti. Eski öneri uçları bir sürüm daha yaşar ve `Deprecation: true` ile halefini bildirir (RFC 8594/9745 biçimi).
 * UT önizlemesi (`/recommend/preview`) eski değildir.
 */
@Component
public class LegacyRecommendationDeprecationFilter extends OncePerRequestFilter {

    private static final Pattern LEGACY = Pattern.compile("^" + Pattern.quote(API_V1) + "/(recommend/(slot-view|slot|transfer-targets)|scouting/\\d+/search)$");
    private static final String SUCCESSOR = "<" + API_V1 + "/careers/{careerId}/recommendations>; rel=\"successor-version\"";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (LEGACY.matcher(request.getRequestURI()).matches()) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Link", SUCCESSOR);
        }
        chain.doFilter(request, response);
    }
}
