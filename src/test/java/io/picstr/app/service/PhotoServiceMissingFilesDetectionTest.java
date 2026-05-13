package io.picstr.app.service;

import io.picstr.app.model.Photo;
import io.picstr.app.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceMissingFilesDetectionTest {

    @Mock
    private StorageService storageService;

    @Mock
    private PhotoRepository photoRepository;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void archivePhotosWithMissingFiles_returnsZeroWhenNoActivePhotos() {
        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of());

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(0);
        verify(photoRepository).findByDeleteDateIsNull(any(Sort.class));
    }

    @Test
    void archivePhotosWithMissingFiles_archivesPhotoWhenOriginalFileIsMissing() {
        var photo = new Photo();
        photo.setId(1L);
        photo.setInternalFilename("photo1.jpg");

        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of(photo));
        when(storageService.get("photo1.jpg")).thenReturn(Optional.empty());
        when(storageService.get("thumb_photo1.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(1);
        verify(photoRepository).save(photo);
        assertThat(photo.getDeleteDate()).isNotNull();
    }

    @Test
    void archivePhotosWithMissingFiles_archivesPhotoWhenThumbnailFileIsMissing() {
        var photo = new Photo();
        photo.setId(1L);
        photo.setInternalFilename("photo2.png");

        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of(photo));
        when(storageService.get("photo2.png")).thenReturn(Optional.of(new StorageObject(null, 0, "image/png")));
        when(storageService.get("thumb_photo2.png")).thenReturn(Optional.empty());

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(1);
        verify(photoRepository).save(photo);
        assertThat(photo.getDeleteDate()).isNotNull();
    }

    @Test
    void archivePhotosWithMissingFiles_archivesPhotoWhenBothFilesAreMissing() {
        var photo = new Photo();
        photo.setId(1L);
        photo.setInternalFilename("photo3.jpg");

        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of(photo));
        when(storageService.get("photo3.jpg")).thenReturn(Optional.empty());
        when(storageService.get("thumb_photo3.jpg")).thenReturn(Optional.empty());

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(1);
        verify(photoRepository).save(photo);
        assertThat(photo.getDeleteDate()).isNotNull();
    }

    @Test
    void archivePhotosWithMissingFiles_keepsPhotoWhenBothFilesExist() {
        var photo = new Photo();
        photo.setId(1L);
        photo.setInternalFilename("photo4.jpg");

        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of(photo));
        when(storageService.get("photo4.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));
        when(storageService.get("thumb_photo4.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(0);
        verify(photoRepository, times(0)).save(photo);
        assertThat(photo.getDeleteDate()).isNull();
    }

    @Test
    void archivePhotosWithMissingFiles_archivesMultiplePhotosWithMissingFiles() {
        var photo1 = new Photo();
        photo1.setId(1L);
        photo1.setInternalFilename("a.jpg");

        var photo2 = new Photo();
        photo2.setId(2L);
        photo2.setInternalFilename("b.jpg");

        var photo3 = new Photo();
        photo3.setId(3L);
        photo3.setInternalFilename("c.jpg");

        when(photoRepository.findByDeleteDateIsNull(any(Sort.class))).thenReturn(List.of(photo1, photo2, photo3));

        // photo1: missing original
        when(storageService.get("a.jpg")).thenReturn(Optional.empty());
        when(storageService.get("thumb_a.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));

        // photo2: has both files
        when(storageService.get("b.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));
        when(storageService.get("thumb_b.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));

        // photo3: missing thumbnail
        when(storageService.get("c.jpg")).thenReturn(Optional.of(new StorageObject(null, 0, "image/jpeg")));
        when(storageService.get("thumb_c.jpg")).thenReturn(Optional.empty());

        var archived = photoService.archivePhotosWithMissingFiles();

        assertThat(archived).isEqualTo(2);
        var inOrder = inOrder(photoRepository);
        inOrder.verify(photoRepository).save(photo1);
        inOrder.verify(photoRepository).save(photo3);
        assertThat(photo1.getDeleteDate()).isNotNull();
        assertThat(photo2.getDeleteDate()).isNull();
        assertThat(photo3.getDeleteDate()).isNotNull();
    }
}
