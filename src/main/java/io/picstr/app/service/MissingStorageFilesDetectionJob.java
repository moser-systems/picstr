package io.picstr.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.photo.missing-files.detection-enabled", havingValue = "true", matchIfMissing = true)
public class MissingStorageFilesDetectionJob {

    private final PhotoService photoService;

    public MissingStorageFilesDetectionJob(PhotoService photoService) {
        this.photoService = photoService;
    }

    @Scheduled(cron = "${app.photo.missing-files.detection-cron:0 0 4 * * *}")
    public void detectAndArchiveMissingFiles() {
        try {
            var archived = photoService.archivePhotosWithMissingFiles();
            if (archived > 0) {
                log.info("Detected and archived {} photos with missing storage files", archived);
            }
        } catch (Exception e) {
            log.error("Error during missing storage files detection", e);
        }
    }
}
