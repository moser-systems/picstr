package io.picstr.app.controller;

import io.picstr.app.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Controller
public class PhotoAssetController {

    private final StorageService storageService;

    public PhotoAssetController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/assets/{key:.+}")
    public ResponseEntity<InputStreamResource> getAsset(@PathVariable String key) {
        if (!isSafeKey(key)) {
            return ResponseEntity.notFound().build();
        }

        var object = storageService.get(key);
        if (object.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var storageObject = object.get();
        var mediaType = parseMediaTypeOrDefault(storageObject.contentType());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(mediaType)
                .contentLength(storageObject.contentLength())
                .body(new InputStreamResource(storageObject.content()));
    }

    private boolean isSafeKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return !key.contains("..")
                && !key.contains("\\")
                && !key.startsWith("/")
                && !key.toLowerCase(Locale.ROOT).startsWith("file:");
    }

    private MediaType parseMediaTypeOrDefault(String value) {
        try {
            if (value == null || value.isBlank()) {
                return MediaType.APPLICATION_OCTET_STREAM;
            }
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
