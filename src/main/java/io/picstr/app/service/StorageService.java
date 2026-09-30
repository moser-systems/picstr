package io.picstr.app.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface StorageService {

    void upload(String key, InputStream content, long contentLength, String contentType);

    Optional<StorageObject> get(String key);

    /** Checks for an object without downloading it. Backends should override the fallback. */
    default boolean exists(String key) {
        return get(key).isPresent();
    }

    List<String> listKeys();

    void delete(String key);
}
