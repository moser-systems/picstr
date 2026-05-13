package io.picstr.app.service;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DummyStorageService implements StorageService {
    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        log.info("Uploading file {} to dummy", key);
    }

    @Override
    public Optional<StorageObject> get(String key) {
        return Optional.empty();
    }

    @Override
    public List<String> listKeys() {
        return List.of();
    }

    @Override
    public void delete(String key) {
        log.info("Deleting file {} from dummy", key);
    }
}
