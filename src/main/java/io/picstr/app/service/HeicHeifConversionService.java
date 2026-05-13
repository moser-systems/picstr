package io.picstr.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;
import org.im4java.core.ConvertCmd;
import org.im4java.core.GMOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class HeicHeifConversionService {

    @Value("${app.thumbnail.gm.search-path:}")
    private String gmSearchPath;

    public boolean isHeicOrHeif(String contentType, String originalFilename) {
        var normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("heic") || normalizedContentType.contains("heif")) {
            return true;
        }

        var lowerFilename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        return lowerFilename.endsWith(".heic") || lowerFilename.endsWith(".heif");
    }

    public byte[] convertHeicToJpeg(byte[] sourceBytes) throws IOException {
        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = Files.createTempFile("picstr-heic-in-", ".heic");
            outputFile = Files.createTempFile("picstr-heic-out-", ".jpg");

            Files.write(inputFile, sourceBytes);

            var cmd = new ConvertCmd(true);
            if (StringUtils.hasText(gmSearchPath)) {
                cmd.setSearchPath(gmSearchPath);
            }

            var op = new GMOperation();
            op.addImage(inputFile.toString());
            op.addImage(outputFile.toString());
            cmd.run(op);

            return Files.readAllBytes(outputFile);
        } catch (Exception exception) {
            log.error("Could not convert HEIC/HEIF image to JPEG", exception);
            throw new IllegalStateException("Could not convert HEIC/HEIF image to JPEG", exception);
        } finally {
            if (inputFile != null) {
                Files.deleteIfExists(inputFile);
            }
            if (outputFile != null) {
                Files.deleteIfExists(outputFile);
            }
        }
    }
}
