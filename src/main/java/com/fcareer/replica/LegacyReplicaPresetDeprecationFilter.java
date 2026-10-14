package com.fcareer.replica;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/**
 * REP-04: replikalar `GET /replicas` ucuna taşındı. `GET /presets?kind=REPLICA` bir sürüm daha yaşar (artık boş liste döner) ve `Deprecation: true` ile halefini bildirir (RFC 8594/9745 biçimi).
 * STYLE ve PERSONAL preset'ler eski değildir.
 */
@Component
public class LegacyReplicaPresetDeprecationFilter extends OncePerRequestFilter {

    private static final String PATH = API_V1 + "/presets";
    private static final String SUCCESSOR = "<" + API_V1 + "/replicas>; rel=\"successor-version\"";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (PATH.equals(request.getRequestURI()) && "REPLICA".equalsIgnoreCase(request.getParameter("kind"))) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Link", SUCCESSOR);
        }
        chain.doFilter(request, response);
    }
}
