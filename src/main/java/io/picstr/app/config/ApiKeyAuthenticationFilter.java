package io.picstr.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates API requests that carry one of the configured keys, either as
 * {@code Authorization: Bearer <key>} or {@code X-API-Key: <key>}. Keys are compared in constant time.
 * Requests without a valid key stay unauthenticated and are rejected by the API security chain.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final List<byte[]> keys;

    public ApiKeyAuthenticationFilter(List<String> keys) {
        this.keys = keys.stream()
                .filter(StringUtils::hasText)
                .map(key -> key.trim().getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var presented = presentedKey(request);
        if (presented != null && isValid(presented)) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    "api-client", null, AuthorityUtils.createAuthorityList("ROLE_API"));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private static String presentedKey(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        var apiKey = request.getHeader(API_KEY_HEADER);
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }

    private boolean isValid(String presented) {
        var bytes = presented.getBytes(StandardCharsets.UTF_8);
        var valid = false;
        for (var key : keys) {
            // No early exit, so the time taken doesn't reveal which key (if any) matched.
            valid |= MessageDigest.isEqual(key, bytes);
        }
        return valid;
    }
}
