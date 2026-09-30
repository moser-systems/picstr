package io.picstr.app.model;

import java.util.Locale;

/**
 * Shared naming rules for categories and tags: trimmed, lower-cased, 2 to 100 characters.
 */
public final class NameRules {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    private NameRules() {
    }

    /**
     * Returns the normalized name, or throws {@link IllegalArgumentException} naming the {@code kind}
     * ("Category", "Tag") if it is blank, shorter than {@link #MIN_LENGTH} or longer than {@link #MAX_LENGTH}.
     */
    public static String normalize(String kind, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(kind + " name is required");
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(kind + " name must be between " + MIN_LENGTH + " and "
                    + MAX_LENGTH + " characters: " + normalized);
        }
        return normalized;
    }
}
