package io.picstr.app.service;

/** Published when an upload has been stored; processing starts once the transaction has committed. */
public record PhotoUploadedEvent(Long photoId) {
}
