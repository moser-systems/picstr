package io.picstr.app.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds {@code currentUser} (display name of the signed-in user) to every view in oauth2 mode, so the
 * layout can show the sign-out button. Basic auth can't be signed out of (the browser resends the
 * credentials) and mode none has no users, so the attribute is only set for oauth2.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Value("${app.security.auth-mode:basic}")
    private String authMode;

    @ModelAttribute("currentUser")
    public String currentUser() {
        if (!"oauth2".equalsIgnoreCase(authMode.trim())) {
            return null;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!trustResolver.isAuthenticated(authentication)) {
            return null;
        }
        return displayName(authentication);
    }

    static String displayName(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return firstNonBlank(oidcUser.getFullName(), oidcUser.getPreferredUsername(), oidcUser.getEmail(),
                    authentication.getName());
        }
        if (principal instanceof OAuth2User oauth2User) {
            return firstNonBlank(oauth2User.getAttribute("name"), oauth2User.getAttribute("login"),
                    oauth2User.getAttribute("email"), authentication.getName());
        }
        return authentication.getName();
    }

    private static String firstNonBlank(Object... values) {
        for (var value : values) {
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }
}
