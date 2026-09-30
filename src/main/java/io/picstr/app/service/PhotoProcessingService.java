package io.picstr.app.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import io.picstr.app.model.ProcessingStatus;
import io.picstr.app.repository.PhotoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the slow part of an upload in the background: HEIC/HEIF originals are converted to JPEG (the
 * converted file replaces the stored original) and the thumbnail is created. Photos end up READY, or
 * FAILED with the original kept as uploaded.
 */
@Slf4j
@Service
public class PhotoProcessingService {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ThumbnailService thumbnailService;
    private final HeicHeifConversionService conversionService;
    private final TaskExecutor executor;

    public PhotoProcessingService(PhotoRepository photoRepository, StorageService storageService,
                                  ThumbnailService thumbnailService, HeicHeifConversionService conversionService,
                                  @Qualifier("photoProcessingExecutor") TaskExecutor executor) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.thumbnailService = thumbnailService;
        this.conversionService = conversionService;
        this.executor = executor;
    }

    /** Queues a freshly uploaded photo once its record is committed, so the worker can see it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPhotoUploaded(PhotoUploadedEvent event) {
        executor.execute(() -> process(event.photoId()));
    }

    /** Picks up photos that were still being processed when the application stopped. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        var pending = photoRepository.findByProcessingStatus(ProcessingStatus.PROCESSING);
        if (!pending.isEmpty()) {
            log.info("Resuming processing of {} photos", pending.size());
            pending.forEach(photo -> executor.execute(() -> process(photo.getId())));
        }
    }

    void process(Long photoId) {
        var photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null || photo.getProcessingStatus() != ProcessingStatus.PROCESSING) {
            return;
        }
        var originalKey = photo.getInternalFilename();
        String convertedKey = null;
        try {
            byte[] bytes;
            try (var original = storageService.get(originalKey)
                    .orElseThrow(() -> new IllegalStateException("Original not found in storage: " + originalKey))
                    .content()) {
                bytes = original.readAllBytes();
            }
            var contentType = photo.getContentType();

            if (conversionService.isHeicOrHeif(contentType, originalKey)) {
                bytes = conversionService.convertHeicToJpeg(bytes);
                contentType = "image/jpeg";
                convertedKey = UUID.randomUUID() + ".jpg";
                storageService.upload(convertedKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
            }
            var finalKey = convertedKey != null ? convertedKey : originalKey;
            thumbnailService.createThumbnail(finalKey, new ByteArrayInputStream(bytes), contentType);

            photo.setInternalFilename(finalKey);
            photo.setContentType(contentType);
            photo.setSizeBytes(bytes.length);
            photo.setProcessingStatus(ProcessingStatus.READY);
            photoRepository.save(photo);
            log.info("Processed photo {} ({})", photoId, finalKey);
        } catch (Exception e) {
            log.error("Failed to process photo {}", photoId, e);
            // Don't leave a converted copy behind: reconciliation would import it as a separate photo
            deleteQuietly(convertedKey);
            markFailed(photoId);
            return;
        }
        if (convertedKey != null) {
            // Only once the record points to the JPEG, so a failure never leaves it without a file
            deleteQuietly(originalKey);
        }
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete {} from storage", key, e);
        }
    }

    private void markFailed(Long photoId) {
        photoRepository.findById(photoId).ifPresent(photo -> {
            photo.setProcessingStatus(ProcessingStatus.FAILED);
            photoRepository.save(photo);
        });
    }
}
