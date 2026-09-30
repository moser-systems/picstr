package io.picstr.app.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login page for app.security.auth-mode=oauth2: lists the configured OAuth2/OIDC providers.
 * Without any provider (basic or none mode) there is nothing to show, so it redirects home.
 */
@Controller
public class LoginController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public LoginController(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) {
        this.clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        Model model) {
        var providers = providers();
        if (providers.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("providers", providers);
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        return "login";
    }

    List<Provider> providers() {
        var providers = new ArrayList<Provider>();
        if (clientRegistrationRepository instanceof Iterable<?> registrations) {
            for (var registration : registrations) {
                if (registration instanceof ClientRegistration client) {
                    providers.add(new Provider(client.getClientName(),
                            "/oauth2/authorization/" + client.getRegistrationId()));
                }
            }
        }
        return providers;
    }

    public record Provider(String name, String url) {
    }
}
