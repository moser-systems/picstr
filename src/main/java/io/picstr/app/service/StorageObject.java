package io.picstr.app.service;

import java.io.InputStream;

public record StorageObject(InputStream content, long contentLength, String contentType) {
}
