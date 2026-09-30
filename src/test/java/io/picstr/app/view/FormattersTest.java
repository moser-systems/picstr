package io.picstr.app.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class FormattersTest {

    private final Formatters fmt = new Formatters();

    @BeforeEach
    void setLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void bytes_formatsWithBinaryUnits() {
        assertThat(fmt.bytes(0)).isEqualTo("0 B");
        assertThat(fmt.bytes(1023)).isEqualTo("1023 B");
        assertThat(fmt.bytes(1536)).isEqualTo("1.5 KB");
        assertThat(fmt.bytes(5L * 1024 * 1024)).isEqualTo("5.0 MB");
    }

    @Test
    void bytes_usesLocaleDecimalSeparator() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(fmt.bytes(1536)).isEqualTo("1,5 KB");
    }
}
