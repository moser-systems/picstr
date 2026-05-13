package io.picstr.app.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface StorageService {

    void upload(String key, InputStream content, long contentLength, String contentType);

    Optional<StorageObject> get(String key);

    List<String> listKeys();

    void delete(String key);
}
