package com.fcareer.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongPredicate;

import static com.fcareer.common.constants.ApiConstants.BEARER_PREFIX;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtService jwtService;
    /** DEPLOY-01 0c: hesap silinince süresi dolmamış access JWT kimlik doğrulamaz (kullanıcı yoksa anonim → korumalı uçta 401). */
    private final LongPredicate userExists;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (Objects.nonNull(header) && header.startsWith(BEARER_PREFIX)) {
            jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()).trim()).filter(user -> userExists.test(user.id())).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            user, null, user.admin() ? AuthorityUtils.createAuthorityList(ROLE_USER, ROLE_ADMIN) : AuthorityUtils.createAuthorityList(ROLE_USER))));
        }
        chain.doFilter(request, response);
    }
}
