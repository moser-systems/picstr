package io.picstr.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingStorageFilesDetectionJobTest {

    @Mock
    private PhotoService photoService;

    @InjectMocks
    private MissingStorageFilesDetectionJob job;

    @Test
    void detectAndArchiveMissingFiles_callsPhotoServiceWhenFilesAreMissing() {
        when(photoService.archivePhotosWithMissingFiles()).thenReturn(3);

        job.detectAndArchiveMissingFiles();

        verify(photoService).archivePhotosWithMissingFiles();
    }

    @Test
    void detectAndArchiveMissingFiles_callsPhotoServiceWhenNoFilesAreMissing() {
        when(photoService.archivePhotosWithMissingFiles()).thenReturn(0);

        job.detectAndArchiveMissingFiles();

        verify(photoService).archivePhotosWithMissingFiles();
    }

    @Test
    void detectAndArchiveMissingFiles_handlesExceptionsGracefully() {
        when(photoService.archivePhotosWithMissingFiles()).thenThrow(new RuntimeException("Storage error"));

        // Should not throw; logs the error instead
        job.detectAndArchiveMissingFiles();

        verify(photoService).archivePhotosWithMissingFiles();
    }
}
