package io.picstr.app.service;

import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.repository.CategoryRepository;
import io.picstr.app.repository.PhotoRepository;
import io.picstr.app.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoStorageReconciliationTest {

    @Mock
    private StorageService storageService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private HeicHeifConversionService conversionService;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void reconcileStorageFiles_returnsZeroWhenStorageIsEmpty() {
        when(storageService.listKeys()).thenReturn(List.of());

        var created = photoService.reconcileStorageFiles();

        assertThat(created).isZero();
        verifyNoInteractions(photoRepository, categoryRepository, thumbnailService);
    }

    @Test
    void reconcileStorageFiles_createsMissingRecordAndThumbnail() {
        var category = new Category("other");
        var imageBytes = "image-data".getBytes();

        when(storageService.listKeys()).thenReturn(List.of("a.jpg", "thumb_a.jpg"));
        when(categoryRepository.findByNameIgnoreCase("other")).thenReturn(Optional.of(category));
        when(photoRepository.findByInternalFilename("a.jpg")).thenReturn(Optional.empty());
        when(storageService.get("a.jpg")).thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(imageBytes), imageBytes.length, "image/jpeg")));
        when(storageService.get("thumb_a.jpg")).thenReturn(Optional.empty());

        var created = photoService.reconcileStorageFiles();

        assertThat(created).isEqualTo(1);

        var photoCaptor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(photoCaptor.capture());

        var saved = photoCaptor.getValue();
        assertThat(saved.getInternalFilename()).isEqualTo("a.jpg");
        assertThat(saved.getOriginalFilename()).isEqualTo("a.jpg");
        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getSizeBytes()).isEqualTo(imageBytes.length);
        assertThat(saved.getCategory()).isEqualTo(category);

        verify(thumbnailService).createThumbnail(any(String.class), any(ByteArrayInputStream.class), any(String.class));
    }

    @Test
    void reconcileStorageFiles_skipsCreateWhenRecordExistsAndThumbnailExists() {
        var category = new Category("other");
        var existing = new Photo();
        var imageBytes = "image-data".getBytes();

        when(storageService.listKeys()).thenReturn(List.of("b.jpg"));
        when(categoryRepository.findByNameIgnoreCase("other")).thenReturn(Optional.of(category));
        when(photoRepository.findByInternalFilename("b.jpg")).thenReturn(Optional.of(existing));
        when(storageService.get("b.jpg")).thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(imageBytes), imageBytes.length, "image/jpeg")));
        when(storageService.get("thumb_b.jpg")).thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(imageBytes), imageBytes.length, "image/jpeg")));

        var created = photoService.reconcileStorageFiles();

        assertThat(created).isZero();
        verify(photoRepository, never()).save(any(Photo.class));
        verify(thumbnailService, never()).createThumbnail(any(String.class), any(ByteArrayInputStream.class), any(String.class));
    }

    @Test
    void reconcileStorageFiles_createsDefaultCategoryIfMissing() {
        var newCategory = new Category("other");
        var imageBytes = "image-data".getBytes();

        when(storageService.listKeys()).thenReturn(List.of("c.jpg"));
        when(categoryRepository.findByNameIgnoreCase("other")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);
        when(photoRepository.findByInternalFilename("c.jpg")).thenReturn(Optional.empty());
        when(storageService.get("c.jpg")).thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(imageBytes), imageBytes.length, "image/jpeg")));
        when(storageService.get("thumb_c.jpg")).thenReturn(Optional.empty());

        var created = photoService.reconcileStorageFiles();

        assertThat(created).isEqualTo(1);
        verify(categoryRepository).save(any(Category.class));
    }
}
