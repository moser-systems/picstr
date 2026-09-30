package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.picstr.app.model.Photo;
import io.picstr.app.model.ProcessingStatus;
import io.picstr.app.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
class PhotoProcessingServiceTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private HeicHeifConversionService conversionService;

    private PhotoProcessingService service;

    @BeforeEach
    void setUp() {
        service = new PhotoProcessingService(photoRepository, storageService, thumbnailService, conversionService,
                new SyncTaskExecutor());
    }

    @Test
    void jpegGetsThumbnailAndBecomesReady() {
        var photo = processing(1L, "a.jpg", "image/jpeg");
        stored("a.jpg", "jpeg-bytes");
        when(conversionService.isHeicOrHeif("image/jpeg", "a.jpg")).thenReturn(false);

        service.onPhotoUploaded(new PhotoUploadedEvent(1L));

        verify(thumbnailService).createThumbnail(eq("a.jpg"), any(InputStream.class), eq("image/jpeg"));
        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.READY);
        assertThat(photo.getInternalFilename()).isEqualTo("a.jpg");
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void heicIsConvertedAndReplacesTheOriginalAfterTheRecordIsSaved() throws Exception {
        var photo = processing(2L, "b.heic", "image/heic");
        stored("b.heic", "heic-bytes");
        when(conversionService.isHeicOrHeif("image/heic", "b.heic")).thenReturn(true);
        when(conversionService.convertHeicToJpeg(any())).thenReturn("jpeg!".getBytes());

        service.onPhotoUploaded(new PhotoUploadedEvent(2L));

        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.READY);
        assertThat(photo.getInternalFilename()).endsWith(".jpg").isNotEqualTo("b.heic");
        assertThat(photo.getContentType()).isEqualTo("image/jpeg");
        assertThat(photo.getSizeBytes()).isEqualTo(5);
        var order = inOrder(storageService, thumbnailService, photoRepository);
        order.verify(storageService).upload(argThat(key -> key.endsWith(".jpg")), any(InputStream.class), eq(5L), eq("image/jpeg"));
        order.verify(thumbnailService).createThumbnail(argThat(key -> key.endsWith(".jpg")), any(InputStream.class), eq("image/jpeg"));
        order.verify(photoRepository).save(photo);
        order.verify(storageService).delete("b.heic");
    }

    @Test
    void failureRemovesTheConvertedCopyAndMarksThePhotoFailed() throws Exception {
        var photo = processing(3L, "c.heic", "image/heic");
        stored("c.heic", "heic-bytes");
        when(conversionService.isHeicOrHeif("image/heic", "c.heic")).thenReturn(true);
        when(conversionService.convertHeicToJpeg(any())).thenReturn("jpeg".getBytes());
        when(thumbnailService.createThumbnail(anyString(), any(InputStream.class), anyString()))
                .thenThrow(new RuntimeException("gm failed"));

        service.onPhotoUploaded(new PhotoUploadedEvent(3L));

        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(photo.getInternalFilename()).isEqualTo("c.heic");
        verify(storageService).delete(argThat(key -> key.endsWith(".jpg")));
        verify(storageService, never()).delete("c.heic");
    }

    @Test
    void missingOriginalMarksThePhotoFailed() {
        var photo = processing(4L, "d.jpg", "image/jpeg");
        when(storageService.get("d.jpg")).thenReturn(Optional.empty());

        service.onPhotoUploaded(new PhotoUploadedEvent(4L));

        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        verifyNoInteractions(thumbnailService);
    }

    @Test
    void readyPhotosAreNotProcessedAgain() {
        var photo = processing(5L, "e.jpg", "image/jpeg");
        photo.setProcessingStatus(ProcessingStatus.READY);

        service.onPhotoUploaded(new PhotoUploadedEvent(5L));

        verifyNoInteractions(storageService, thumbnailService);
    }

    @Test
    void unfinishedPhotosAreResumedOnStartup() {
        var photo = processing(6L, "f.jpg", "image/jpeg");
        when(photoRepository.findByProcessingStatus(ProcessingStatus.PROCESSING)).thenReturn(List.of(photo));
        stored("f.jpg", "jpeg-bytes");

        service.resumeUnfinished();

        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.READY);
    }

    private Photo processing(long id, String key, String contentType) {
        var photo = new Photo();
        photo.setId(id);
        photo.setInternalFilename(key);
        photo.setContentType(contentType);
        photo.setProcessingStatus(ProcessingStatus.PROCESSING);
        when(photoRepository.findById(id)).thenReturn(Optional.of(photo));
        return photo;
    }

    private void stored(String key, String content) {
        var bytes = content.getBytes();
        when(storageService.get(key)).thenReturn(Optional.of(
                new StorageObject(new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream")));
    }
}
