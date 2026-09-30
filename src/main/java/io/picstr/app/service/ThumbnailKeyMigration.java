package io.picstr.app.service;

import java.io.IOException;

import io.picstr.app.model.ThumbnailKeys;
import io.picstr.app.repository.PhotoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Moves thumbnails of non-JPEG originals from their legacy key ({@code thumb_abc.png}) to the current
 * one ({@code thumb_abc.png.jpg}) once at startup. Idempotent: photos that are already migrated, or
 * have no legacy thumbnail, are skipped; failures are logged and retried on the next start.
 */
@Slf4j
@Service
public class ThumbnailKeyMigration {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;

    public ThumbnailKeyMigration(PhotoRepository photoRepository, StorageService storageService) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        var migrated = migrate();
        if (migrated > 0) {
            log.info("Moved {} thumbnails to their .jpg key", migrated);
        }
    }

    int migrate() {
        int migrated = 0;
        for (var photo : photoRepository.findAll()) {
            var original = photo.getInternalFilename();
            var legacyKey = ThumbnailKeys.legacyForOriginal(original);
            var currentKey = ThumbnailKeys.forOriginal(original);
            if (legacyKey.equals(currentKey)) {
                continue;
            }
            try {
                if (storageService.exists(currentKey)) {
                    continue;
                }
                var legacy = storageService.get(legacyKey);
                if (legacy.isEmpty()) {
                    continue;
                }
                var thumbnail = legacy.get();
                try (var content = thumbnail.content()) {
                    storageService.upload(currentKey, content, thumbnail.contentLength(), "image/jpeg");
                }
                storageService.delete(legacyKey);
                migrated++;
            } catch (RuntimeException | IOException e) {
                log.error("Failed to move thumbnail {} to {}", legacyKey, currentKey, e);
            }
        }
        return migrated;
    }
}
