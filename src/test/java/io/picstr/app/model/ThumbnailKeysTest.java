package io.picstr.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThumbnailKeysTest {

    @Test
    void forOriginal_alwaysEndsInJpg() {
        assertThat(ThumbnailKeys.forOriginal("abc.jpg")).isEqualTo("thumb_abc.jpg");
        assertThat(ThumbnailKeys.forOriginal("abc.JPG")).isEqualTo("thumb_abc.JPG");
        assertThat(ThumbnailKeys.forOriginal("abc.png")).isEqualTo("thumb_abc.png.jpg");
        assertThat(ThumbnailKeys.forOriginal("abc.jpeg")).isEqualTo("thumb_abc.jpeg.jpg");
    }

    @Test
    void forOriginal_keepsKeysUniquePerOriginal() {
        assertThat(ThumbnailKeys.forOriginal("abc.png")).isNotEqualTo(ThumbnailKeys.forOriginal("abc.gif"));
    }

    @Test
    void legacyForOriginal_isPrefixOnly() {
        assertThat(ThumbnailKeys.legacyForOriginal("abc.png")).isEqualTo("thumb_abc.png");
    }

    @Test
    void originalCandidates_coverBothShapes() {
        assertThat(ThumbnailKeys.originalCandidates("thumb_abc.jpg")).containsExactly("abc.jpg", "abc");
        assertThat(ThumbnailKeys.originalCandidates("thumb_abc.png.jpg")).containsExactly("abc.png.jpg", "abc.png");
        assertThat(ThumbnailKeys.originalCandidates("thumb_abc.png")).containsExactly("abc.png");
    }

    @Test
    void photoExposesThumbnailKey() {
        var photo = new Photo();
        photo.setInternalFilename("abc.webp");

        assertThat(photo.getThumbnailKey()).isEqualTo("thumb_abc.webp.jpg");
    }
}
