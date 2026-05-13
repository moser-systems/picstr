package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import io.picstr.app.model.Photo;
import io.picstr.app.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private PhotoRepository photoRepository;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void purgeArchivedOlderThanDays_returnsZeroWhenNoCandidates() {
        when(photoRepository.findByDeleteDateBefore(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(List.of());

        var purged = photoService.purgeArchivedOlderThanDays(30);

        assertThat(purged).isZero();
        verifyNoInteractions(storageService);
        verify(photoRepository, never()).deleteAllInBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void purgeArchivedOlderThanDays_deletesThumbnailAndOriginalBeforeDbDelete() {
        var first = new Photo();
        first.setInternalFilename("a.jpg");
        var second = new Photo();
        second.setInternalFilename("b.png");
        var candidates = List.of(first, second);

        when(photoRepository.findByDeleteDateBefore(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(candidates);

        var purged = photoService.purgeArchivedOlderThanDays(7);

        assertThat(purged).isEqualTo(2);
        var inOrder = inOrder(storageService, photoRepository);
        inOrder.verify(storageService).delete("thumb_a.jpg");
        inOrder.verify(storageService).delete("a.jpg");
        inOrder.verify(storageService).delete("thumb_b.png");
        inOrder.verify(storageService).delete("b.png");
        inOrder.verify(photoRepository).deleteAllInBatch(candidates);
    }
}
