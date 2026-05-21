package io.picstr.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

class WebLangConfigTest {

    private final WebLangConfig config = new WebLangConfig();

    @Test
    void usesAcceptLanguageAsDefaultWhenSupported() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE,de;q=0.9,en;q=0.8");

        Locale locale = resolver.resolveLocale(request);

        assertThat(locale).isEqualTo(Locale.GERMAN);
    }

    @Test
    void usesFrenchWhenBrowserRequestsFrench() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");

        Locale locale = resolver.resolveLocale(request);

        assertThat(locale).isEqualTo(Locale.FRENCH);
    }

    @Test
    void fallsBackToEnglishWhenAcceptLanguageIsUnsupported() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "xx-XX,xx;q=0.9");

        Locale locale = resolver.resolveLocale(request);

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void langQueryParameterOverridesSessionLocale() throws Exception {
        LocaleResolver resolver = config.localeResolver();
        LocaleChangeInterceptor interceptor = config.localeChangeInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addParameter("lang", "de");
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);

        interceptor.preHandle(request, response, new Object());

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.GERMAN);
    }
}
