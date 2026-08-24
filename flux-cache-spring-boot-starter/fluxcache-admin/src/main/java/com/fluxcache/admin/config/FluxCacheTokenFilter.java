package com.fluxcache.admin.config;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests without a matching {@code X-Flux-Cache-Token} header when a
 * token is configured. No-op when the token is blank.
 *
 * @author : wh
 */
public class FluxCacheTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Flux-Cache-Token";

    private final String token;

    public FluxCacheTokenFilter(String token) {
        this.token = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || token.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(TOKEN_HEADER);
        if (token.equals(provided)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing " + TOKEN_HEADER);
    }
}
