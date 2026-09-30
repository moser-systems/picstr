package io.picstr.app.config;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Picstr application.
 * Protects all controller endpoints except /assets/** and /actuator/health which are publicly accessible.
 * Assets of archived photos are the exception: they require authentication (see ArchivedAssetAuthorizationManager).
 *
 * Supports authentication via:
 * - OAuth2/OpenID Connect (app.security.auth-mode=oauth2)
 * - HTTP Basic authentication (app.security.auth-mode=basic)
 * - Disabled authentication (app.security.auth-mode=none)
 *
 * CSRF protection is enabled in the basic and oauth2 modes.
 *
 * Legacy compatibility: app.security.authentication-enabled=false forces auth-mode=none.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.auth-mode:basic}")
    private String authMode;

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final ArchivedAssetAuthorizationManager archivedAssetAuthorizationManager;

    public SecurityConfig(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
                          ArchivedAssetAuthorizationManager archivedAssetAuthorizationManager) {
        this.clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        this.archivedAssetAuthorizationManager = archivedAssetAuthorizationManager;
    }

    /**
     * The REST API under /api/** authenticates with API keys (app.api.keys) instead of the web login: no
     * session, no CSRF (no cookies are involved) and 401 instead of a login redirect. In auth mode none the
     * API is open like the rest of the application.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              @Value("${app.api.keys:}") List<String> apiKeys) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,"
                        + "\"detail\":\"A valid API key is required (Authorization: Bearer <key>).\"}");
            }));

        if (resolveAuthMode() == AuthMode.NONE) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        http.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthMode resolvedMode = resolveAuthMode();

        if (resolvedMode == AuthMode.NONE) {
            // Disable all security when auth mode is none.
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());

            return http.build();
        }

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/assets/{key}").access(archivedAssetAuthorizationManager)
                .requestMatchers("/assets/**").permitAll()
                .requestMatchers("/vendor/**").permitAll()
                .requestMatchers("/*.ico").permitAll()
                .requestMatchers("/*.png").permitAll()
                .requestMatchers("/*.jpg").permitAll()
                .requestMatchers("/*.svg").permitAll()
                .requestMatchers("/*.txt").permitAll()
                .requestMatchers("/site.webmanifest").permitAll()
                .requestMatchers("/login**").permitAll()
                .requestMatchers("/error**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated());

        if (resolvedMode == AuthMode.OAUTH2) {
            if (clientRegistrationRepository == null) {
                throw new IllegalStateException("Auth mode is 'oauth2' but no OAuth2 client registration is configured. "
                    + "Set spring.security.oauth2.client.registration.* properties or switch app.security.auth-mode to 'basic' or 'none'.");
            }

            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .defaultSuccessUrl("/"))
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        } else {
            http.httpBasic(basic -> {});
        }

        // CSRF protection stays on (Spring Security default). Forms rendered with th:action get the
        // token automatically; htmx requests get it from the _csrf meta tags in layout.html.

        return http.build();
    }

    private AuthMode resolveAuthMode() {
        try {
            return AuthMode.valueOf(authMode.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid app.security.auth-mode='" + authMode
                + "'. Supported values: oauth2, basic, none.", e);
        }
    }

    private enum AuthMode {
        OAUTH2,
        BASIC,
        NONE
    }
}
