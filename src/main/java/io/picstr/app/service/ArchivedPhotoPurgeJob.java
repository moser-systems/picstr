package io.picstr.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArchivedPhotoPurgeJob {

    private final PhotoService photoService;

    @Value("${app.photo.archive.retention-days:30}")
    private int retentionDays;

    public ArchivedPhotoPurgeJob(PhotoService photoService) {
        this.photoService = photoService;
    }

    @Scheduled(cron = "${app.photo.archive.purge-cron:0 0 3 * * *}")
    public void purgeArchivedPhotos() {
        var deleted = photoService.purgeArchivedOlderThanDays(retentionDays);
        if (deleted > 0) {
            log.info("Purged {} archived photos older than {} days", deleted, retentionDays);
        }
    }
}
