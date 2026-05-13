package io.picstr.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.photo.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class StoragePhotoReconciliationJob {

    private final PhotoService photoService;

    public StoragePhotoReconciliationJob(PhotoService photoService) {
        this.photoService = photoService;
    }

    @Scheduled(cron = "${app.photo.reconcile.cron:0 15 4 * * *}")
    public void reconcilePhotoRecords() {
        var created = photoService.reconcileStorageFiles();
        if (created > 0) {
            log.info("Created {} missing photo records from storage reconciliation", created);
        }
    }
}
