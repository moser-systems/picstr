package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.picstr.app.model.Photo;
import io.picstr.app.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThumbnailKeyMigrationTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ThumbnailKeyMigration migration;

    @Test
    void movesLegacyThumbnailOfNonJpegOriginal() {
        when(photoRepository.findAll()).thenReturn(List.of(photo("a.png")));
        when(storageService.exists("thumb_a.png.jpg")).thenReturn(false);
        var bytes = "jpeg".getBytes();
        when(storageService.get("thumb_a.png"))
                .thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(bytes), bytes.length, "image/png")));

        assertThat(migration.migrate()).isEqualTo(1);

        verify(storageService).upload(eq("thumb_a.png.jpg"), any(InputStream.class), eq((long) bytes.length), eq("image/jpeg"));
        verify(storageService).delete("thumb_a.png");
    }

    @Test
    void skipsJpegOriginals() {
        when(photoRepository.findAll()).thenReturn(List.of(photo("a.jpg")));

        assertThat(migration.migrate()).isZero();

        verifyNoInteractions(storageService);
    }

    @Test
    void skipsAlreadyMigratedThumbnails() {
        when(photoRepository.findAll()).thenReturn(List.of(photo("a.png")));
        when(storageService.exists("thumb_a.png.jpg")).thenReturn(true);

        assertThat(migration.migrate()).isZero();

        verify(storageService, never()).get(anyString());
        verify(storageService, never()).upload(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void skipsPhotosWithoutLegacyThumbnail() {
        when(photoRepository.findAll()).thenReturn(List.of(photo("a.gif")));
        when(storageService.exists("thumb_a.gif.jpg")).thenReturn(false);
        when(storageService.get("thumb_a.gif")).thenReturn(Optional.empty());

        assertThat(migration.migrate()).isZero();

        verify(storageService, never()).delete(anyString());
    }

    private static Photo photo(String internalFilename) {
        var photo = new Photo();
        photo.setInternalFilename(internalFilename);
        return photo;
    }
}
