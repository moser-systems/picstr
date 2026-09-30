package io.picstr.app.view;

import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * View helpers, available in templates as {@code @fmt}.
 */
@Component("fmt")
public class Formatters {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    public String bytes(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double value = size;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return String.format(locale, "%.1f %s", value, UNITS[unit]);
    }
}
