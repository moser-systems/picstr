package io.picstr.app.model;

import java.util.List;
import java.util.Locale;

/**
 * Storage key convention for thumbnails. Thumbnails are always JPEG, so their key ends in ".jpg":
 * {@code thumb_<original>} for JPEG originals ({@code thumb_abc.jpg}) and {@code thumb_<original>.jpg}
 * otherwise ({@code thumb_abc.png.jpg}), which keeps thumbnail keys unique per original.
 */
public final class ThumbnailKeys {

    public static final String PREFIX = "thumb_";
    private static final String JPG = ".jpg";

    private ThumbnailKeys() {
    }

    public static String forOriginal(String originalKey) {
        return PREFIX + originalKey + (isJpg(originalKey) ? "" : JPG);
    }

    /** Key used before thumbnails always ended in ".jpg"; differs from {@link #forOriginal} for non-JPEG originals. */
    public static String legacyForOriginal(String originalKey) {
        return PREFIX + originalKey;
    }

    public static boolean isThumbnail(String key) {
        return key.startsWith(PREFIX);
    }

    /** Original keys a thumbnail key can belong to: {@code thumb_a.png.jpg} → a.png.jpg or a.png. */
    public static List<String> originalCandidates(String thumbnailKey) {
        var rest = thumbnailKey.substring(PREFIX.length());
        if (isJpg(rest) && rest.length() > JPG.length()) {
            return List.of(rest, rest.substring(0, rest.length() - JPG.length()));
        }
        return List.of(rest);
    }

    private static boolean isJpg(String key) {
        return key.toLowerCase(Locale.ROOT).endsWith(JPG);
    }
}
