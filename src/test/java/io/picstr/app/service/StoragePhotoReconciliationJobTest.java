package io.picstr.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoragePhotoReconciliationJobTest {

    @Mock
    private PhotoService photoService;

    @InjectMocks
    private StoragePhotoReconciliationJob job;

    @Test
    void reconcilePhotoRecords_invokesReconciliation() {
        when(photoService.reconcileStorageFiles()).thenReturn(0);

        job.reconcilePhotoRecords();

        verify(photoService).reconcileStorageFiles();
    }
}
