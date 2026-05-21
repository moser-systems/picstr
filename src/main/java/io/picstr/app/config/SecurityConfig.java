package io.picstr.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Picstr application.
 * Protects all controller endpoints except /assets/** which is publicly accessible.
 *
 * Supports authentication via:
 * - OAuth2/OpenID Connect (app.security.auth-mode=oauth2)
 * - HTTP Basic authentication (app.security.auth-mode=basic)
 * - Disabled authentication (app.security.auth-mode=none)
 *
 * Legacy compatibility: app.security.authentication-enabled=false forces auth-mode=none.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.auth-mode:basic}")
    private String authMode;

    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) {
        this.clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthMode resolvedMode = resolveAuthMode();

        if (resolvedMode == AuthMode.NONE) {
            // Disable all security when auth mode is none.
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());

            return http.build();
        }

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/assets/**").permitAll()
                .requestMatchers("/vendor/**").permitAll()
                .requestMatchers("/logo_*.png").permitAll()
                .requestMatchers("/login**").permitAll()
                .requestMatchers("/error**").permitAll()
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

        http.csrf(csrf -> csrf.disable());

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
