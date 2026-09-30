package io.picstr.app.service;

import java.io.InputStream;

public interface ThumbnailService {

    /**
     * Creates a thumbnail from the given image stream and uploads it via
     * the active {@link StorageService}.
     *
     * @param originalKey the storage key of the original image (e.g. "abc123.jpg")
     * @param content     the raw image bytes
     * @param contentType the MIME type of the original image
     * @return the storage key of the generated thumbnail, see {@link io.picstr.app.model.ThumbnailKeys}
     */
    String createThumbnail(String originalKey, InputStream content, String contentType);
}
