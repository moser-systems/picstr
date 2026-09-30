package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

class CurrentUserAdviceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oidcUser_showsFullName() {
        var idToken = OidcIdToken.withTokenValue("token").claim("sub", "123").claim("name", "Jane Doe").build();
        signIn(new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), idToken), List.of(), "microsoft"));

        assertThat(advice("oauth2").currentUser()).isEqualTo("Jane Doe");
    }

    @Test
    void oauth2User_fallsBackToLoginAttribute() {
        var user = new DefaultOAuth2User(List.of(), Map.of("id", "42", "login", "jdoe"), "id");
        signIn(new OAuth2AuthenticationToken(user, List.of(), "github"));

        assertThat(advice("oauth2").currentUser()).isEqualTo("jdoe");
    }

    @Test
    void anonymousUser_hasNoCurrentUser() {
        signIn(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(advice("oauth2").currentUser()).isNull();
    }

    @Test
    void basicMode_hasNoCurrentUser() {
        signIn(UsernamePasswordAuthenticationToken.authenticated("user", null, List.of()));

        assertThat(advice("basic").currentUser()).isNull();
    }

    private static CurrentUserAdvice advice(String authMode) {
        var advice = new CurrentUserAdvice();
        ReflectionTestUtils.setField(advice, "authMode", authMode);
        return advice;
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
