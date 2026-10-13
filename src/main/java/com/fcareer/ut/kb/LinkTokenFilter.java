package com.fcareer.ut.kb;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** `X-Link-Token` başlığıyla extension'ın yalnız yakalama/katkı uçlarına kullanıcı adına erişmesini sağlar; başka hiçbir uç bu başlığı kabul etmez. */
@RequiredArgsConstructor
public class LinkTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Link-Token";
    /** İstek özniteliği: bağlama oturumunun modu (PERSONAL | CONTRIB); JWT ile gelen isteklerde yoktur. */
    public static final String MODE_ATTRIBUTE = "fc.linkMode";
    public static final String MODE_CONTRIB = "CONTRIB";
    private static final String CAPTURE = API_V1 + "/ut/capture";
    private static final String CONTRIB = API_V1 + "/ut/contrib";

    private final LinkTokenStore store;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return Objects.isNull(request.getHeader(HEADER)) || !(path.equals(CAPTURE) || path.startsWith(CONTRIB + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        store.resolve(request.getHeader(HEADER)).ifPresent(grant -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AuthUser(grant.userId(), null), grant.mode(), AuthorityUtils.createAuthorityList(JwtAuthenticationFilter.ROLE_USER)));
            request.setAttribute(MODE_ATTRIBUTE, grant.mode());
        });
        chain.doFilter(request, response);
    }
}
