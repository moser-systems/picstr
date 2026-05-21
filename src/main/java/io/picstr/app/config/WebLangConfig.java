package io.picstr.app.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class WebLangConfig implements WebMvcConfigurer {
    private static final List<Locale> SUPPORTED_LOCALES = List.of(
        Locale.ENGLISH,
        Locale.GERMAN,
        Locale.FRENCH,
        Locale.ITALIAN,
        Locale.forLanguageTag("es"), // Spanish
        Locale.forLanguageTag("pt"), // Portuguese
        Locale.JAPANESE,
        Locale.SIMPLIFIED_CHINESE
    );

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocaleFunction(request -> {
            Locale requested = request.getLocale();
            if (requested == null) {
                return Locale.ENGLISH;
            }
            return SUPPORTED_LOCALES.stream()
                .filter(locale -> locale.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(Locale.ENGLISH);
        });
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Bean
    public ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
