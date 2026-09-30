package io.picstr.app.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Adds {@code pageBaseUrl} to every view: the current URL with its query parameters (e.g. search
 * criteria) but without paging, ending in "?" or "&" so templates can append {@code page=..&size=..}.
 * Also adds {@code returnUrl}, the current URL with its query, for forms that should come back here.
 * Computed here because Thymeleaf doesn't allow bean calls inside th:href.
 */
@ControllerAdvice
public class PageLinksAdvice {

    @ModelAttribute("returnUrl")
    public String returnUrl() {
        var uri = ServletUriComponentsBuilder.fromCurrentRequest().build();
        var query = uri.getQuery();
        return uri.getPath() + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    @ModelAttribute("pageBaseUrl")
    public String pageBaseUrl() {
        var uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page")
                .replaceQueryParam("size")
                .build();
        var query = uri.getQuery();
        return uri.getPath() + (query == null || query.isEmpty() ? "?" : "?" + query + "&");
    }
}
