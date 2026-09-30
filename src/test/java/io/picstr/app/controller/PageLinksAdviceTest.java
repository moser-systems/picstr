package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PageLinksAdviceTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void keepsOtherQueryParametersAndDropsPaging() {
        var request = new MockHttpServletRequest("GET", "/photos/search");
        request.setQueryString("q=beach&page=3&tag=sunset&size=12");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(new PageLinksAdvice().pageBaseUrl()).isEqualTo("/photos/search?q=beach&tag=sunset&");
    }

    @Test
    void plainUrlGetsQuestionMark() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/photos")));

        assertThat(new PageLinksAdvice().pageBaseUrl()).isEqualTo("/photos?");
    }
}
