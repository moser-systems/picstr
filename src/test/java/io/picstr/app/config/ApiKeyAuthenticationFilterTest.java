package io.picstr.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthenticationFilterTest {

    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(List.of("key-one", " key-two ", ""));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsBearerToken() throws Exception {
        assertThat(authenticate("Authorization", "Bearer key-two")).isTrue();
    }

    @Test
    void acceptsApiKeyHeader() throws Exception {
        assertThat(authenticate("X-API-Key", "key-one")).isTrue();
    }

    @Test
    void rejectsUnknownKeyBasicCredentialsAndMissingHeader() throws Exception {
        assertThat(authenticate("Authorization", "Bearer nope")).isFalse();
        assertThat(authenticate("Authorization", "Basic dTpw")).isFalse();
        assertThat(authenticate(null, null)).isFalse();
    }

    @Test
    void blankConfiguredKeysNeverMatch() throws Exception {
        assertThat(authenticate("Authorization", "Bearer ")).isFalse();
        assertThat(authenticate("X-API-Key", " ")).isFalse();
    }

    private boolean authenticate(String header, String value) throws Exception {
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest("GET", "/api/v1/photos");
        if (header != null) {
            request.addHeader(header, value);
        }
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("filter always continues the chain").isNotNull();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
