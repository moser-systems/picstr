package io.picstr.app.service;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import io.picstr.app.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        try {
            Path basePath = Paths.get(properties.getLocal().getBasePath());

            // Create base directory if it doesn't exist
            Files.createDirectories(basePath);

            Path filePath = basePath.resolve(key);

            // Create parent directories if needed
            Files.createDirectories(filePath.getParent());

            // Write the file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = content.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            log.info("File uploaded to local storage: {}", filePath);
        } catch (Exception e) {
            log.error("Failed to upload file to local storage: {}", key, e);
            throw new RuntimeException("Failed to upload file: " + key, e);
        }
    }

    @Override
    public Optional<StorageObject> get(String key) {
        try {
            Path basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(key).normalize();

            if (!filePath.startsWith(basePath) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                return Optional.empty();
            }

            var bytes = Files.readAllBytes(filePath);
            var contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return Optional.of(new StorageObject(new ByteArrayInputStream(bytes), bytes.length, contentType));
        } catch (Exception e) {
            log.error("Failed to get file from local storage: {}", key, e);
            throw new RuntimeException("Failed to get file: " + key, e);
        }
    }

    @Override
    public List<String> listKeys() {
        try {
            Path basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
            if (!Files.exists(basePath)) {
                return List.of();
            }

            try (var stream = Files.walk(basePath)) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(path -> basePath.relativize(path).toString().replace('\\', '/'))
                        .toList();
            }
        } catch (Exception e) {
            log.error("Failed to list files from local storage", e);
            throw new RuntimeException("Failed to list files from local storage", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(key).normalize();

            if (!filePath.startsWith(basePath)) {
                throw new IllegalArgumentException("Invalid storage key path: " + key);
            }

            Files.deleteIfExists(filePath);
            log.info("File deleted from local storage: {}", filePath);
        } catch (Exception e) {
            log.error("Failed to delete file from local storage: {}", key, e);
            throw new RuntimeException("Failed to delete file: " + key, e);
        }
    }
}
