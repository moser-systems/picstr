package io.picstr.app.service;

/**
 * A requested photo, category or tag doesn't exist. Extends {@link IllegalArgumentException} so the web
 * controllers keep handling it like other invalid input; the API maps it to 404.
 */
public class NotFoundException extends IllegalArgumentException {

    public NotFoundException(String message) {
        super(message);
    }
}
