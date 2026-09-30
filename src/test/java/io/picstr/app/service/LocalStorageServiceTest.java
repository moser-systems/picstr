package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.picstr.app.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

    @TempDir
    Path root;

    private Path base;
    private LocalStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        base = Files.createDirectory(root.resolve("uploads"));
        var properties = new StorageProperties();
        properties.getLocal().setBasePath(base.toString());
        storage = new LocalStorageService(properties);
    }

    @Test
    void uploadGetAndDeleteRoundTrip() throws Exception {
        upload("photo.jpg", "data");

        assertThat(Files.readString(base.resolve("photo.jpg"))).isEqualTo("data");
        var object = storage.get("photo.jpg").orElseThrow();
        assertThat(object.contentLength()).isEqualTo(4);
        try (var content = object.content()) {
            assertThat(new String(content.readAllBytes())).isEqualTo("data");
        }
        assertThat(storage.listKeys()).containsExactly("photo.jpg");

        storage.delete("photo.jpg");
        assertThat(storage.get("photo.jpg")).isEmpty();
    }

    @Test
    void upload_rejectsKeyOutsideBasePath() {
        assertThatThrownBy(() -> upload("../escape.jpg", "x")).isInstanceOf(RuntimeException.class);
        assertThat(root.resolve("escape.jpg")).doesNotExist();
    }

    @Test
    void get_returnsEmptyForKeyOutsideBasePath() throws Exception {
        Files.writeString(root.resolve("secret.txt"), "secret");

        assertThat(storage.get("../secret.txt")).isEmpty();
    }

    @Test
    void delete_rejectsKeyOutsideBasePath() throws Exception {
        Files.writeString(root.resolve("keep.txt"), "keep");

        assertThatThrownBy(() -> storage.delete("../keep.txt")).isInstanceOf(RuntimeException.class);
        assertThat(root.resolve("keep.txt")).exists();
    }

    @Test
    void exists_checksWithoutReading() {
        upload("photo.jpg", "data");

        assertThat(storage.exists("photo.jpg")).isTrue();
        assertThat(storage.exists("missing.jpg")).isFalse();
        assertThat(storage.exists("../photo.jpg")).isFalse();
    }

    private void upload(String key, String content) {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.upload(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
    }
}
