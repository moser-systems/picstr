package io.picstr.app.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.extern.slf4j.Slf4j;
import org.im4java.core.ConvertCmd;
import org.im4java.core.GMOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.thumbnail.engine", havingValue = "graphicsmagick")
public class GraphicsMagickThumbnailService implements ThumbnailService {

    static final int THUMBNAIL_WIDTH = 256;
    static final int THUMBNAIL_HEIGHT = 256;
    static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    static final String THUMBNAIL_KEY_PREFIX = "thumb_";

    private final StorageService storageService;
    private final String gmSearchPath;

    public GraphicsMagickThumbnailService(
            StorageService storageService,
            @Value("${app.thumbnail.gm.search-path:}") String gmSearchPath
    ) {
        this.storageService = storageService;
        this.gmSearchPath = gmSearchPath;
    }

    @Override
    public String createThumbnail(String originalKey, InputStream content, String contentType) {
        var thumbnailKey = THUMBNAIL_KEY_PREFIX + originalKey;
        Path inputFile = null;
        Path outputFile = null;

        try {
            inputFile = Files.createTempFile("picstr-thumb-in-", ".img");
            outputFile = Files.createTempFile("picstr-thumb-out-", ".jpg");
            Files.copy(content, inputFile, StandardCopyOption.REPLACE_EXISTING);

            var cmd = new ConvertCmd(true);
            if (StringUtils.hasText(gmSearchPath)) {
                cmd.setSearchPath(gmSearchPath);
            }

            var op = new GMOperation();
            op.addImage(inputFile.toString());
            op.resize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            op.quality(85.0);
            op.addImage(outputFile.toString());

            cmd.run(op);

            var bytes = Files.readAllBytes(outputFile);
            storageService.upload(thumbnailKey, new ByteArrayInputStream(bytes), bytes.length, THUMBNAIL_CONTENT_TYPE);
            log.info("GraphicsMagick thumbnail created and uploaded: {}", thumbnailKey);
            return thumbnailKey;
        } catch (Exception e) {
            log.error("Failed to create GraphicsMagick thumbnail for: {}", originalKey, e);
            throw new RuntimeException("Failed to create thumbnail for: " + originalKey, e);
        } finally {
            try {
                if (inputFile != null) {
                    Files.deleteIfExists(inputFile);
                }
                if (outputFile != null) {
                    Files.deleteIfExists(outputFile);
                }
            } catch (Exception cleanupError) {
                log.warn("Failed to clean up temporary thumbnail files", cleanupError);
            }
        }
    }
}
