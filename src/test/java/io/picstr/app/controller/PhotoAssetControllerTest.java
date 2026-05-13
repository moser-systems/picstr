package io.picstr.app.controller;

import io.picstr.app.service.StorageObject;
import io.picstr.app.service.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoAssetControllerTest {

    @Test
    void getAsset_returnsBytesAndContentTypeForExistingObject() throws Exception {
        var storageService = mock(StorageService.class);
        var controller = new PhotoAssetController(storageService);

        var payload = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(storageService.get("thumb_a.jpg"))
                .thenReturn(Optional.of(new StorageObject(new ByteArrayInputStream(payload), payload.length, "image/jpeg")));

        var response = controller.getAsset("thumb_a.jpg");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("image/jpeg");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(payload);
        verify(storageService).get("thumb_a.jpg");
    }

    @Test
    void getAsset_returnsNotFoundForMissingObject() {
        var storageService = mock(StorageService.class);
        var controller = new PhotoAssetController(storageService);

        when(storageService.get("missing.jpg")).thenReturn(Optional.empty());

        var response = controller.getAsset("missing.jpg");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(storageService).get("missing.jpg");
    }

    @Test
    void getAsset_returnsNotFoundForUnsafeKey() {
        var storageService = mock(StorageService.class);
        var controller = new PhotoAssetController(storageService);

        var response = controller.getAsset("../etc/passwd");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storageService);
    }
}
