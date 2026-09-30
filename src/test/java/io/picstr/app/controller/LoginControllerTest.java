package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.ui.ExtendedModelMap;

class LoginControllerTest {

    @Test
    void login_listsConfiguredProviders() {
        var controller = new LoginController(provider(new InMemoryClientRegistrationRepository(
                registration("microsoft", "Microsoft"), registration("google", "Google"))));
        var model = new ExtendedModelMap();

        var view = controller.login(null, null, model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("providers")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder(
                        new LoginController.Provider("Microsoft", "/oauth2/authorization/microsoft"),
                        new LoginController.Provider("Google", "/oauth2/authorization/google"));
        assertThat(model.getAttribute("loginError")).isEqualTo(false);
        assertThat(model.getAttribute("loggedOut")).isEqualTo(false);
    }

    @Test
    void login_showsErrorAndLogoutMessages() {
        var controller = new LoginController(provider(new InMemoryClientRegistrationRepository(
                registration("microsoft", "Microsoft"))));
        var model = new ExtendedModelMap();

        controller.login("", "", model);

        assertThat(model.getAttribute("loginError")).isEqualTo(true);
        assertThat(model.getAttribute("loggedOut")).isEqualTo(true);
    }

    @Test
    void login_redirectsHomeWithoutProviders() {
        var controller = new LoginController(provider(null));

        assertThat(controller.login(null, null, new ExtendedModelMap())).isEqualTo("redirect:/");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ClientRegistrationRepository> provider(ClientRegistrationRepository repository) {
        ObjectProvider<ClientRegistrationRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        return provider;
    }

    private static ClientRegistration registration(String id, String name) {
        return ClientRegistration.withRegistrationId(id)
                .clientId("client")
                .clientName(name)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://idp.example/authorize")
                .tokenUri("https://idp.example/token")
                .build();
    }
}
