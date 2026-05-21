package io.picstr.app.controller;

import io.picstr.app.model.Photo;
import io.picstr.app.service.PhotoService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedControllerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recentFeed_returnsRssWithoutThumbnailOrDetailLinks() {
        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("picstr.example.com");
        request.setServerPort(443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var photoService = mock(PhotoService.class);
        when(photoService.recentForFeed(20)).thenReturn(List.of(samplePhoto()));

        var controller = new FeedController(photoService);
        var response = controller.recentFeed(20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("application/rss+xml");
        assertThat(response.getBody())
            .contains("<rss")
            .contains("version=\"2.0\"")
            .contains("<title>IMG_0001.jpg</title>")
            .contains("<link>https://picstr.example.com/assets/thumb_abc.jpg</link>")
            .doesNotContain("/photos/")
            .contains("thumb_");

        verify(photoService).recentForFeed(20);
    }

    @Test
    void recentFeed_escapesXmlCharacters() {
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var photo = samplePhoto();
        photo.setOriginalFilename("A&B <test>.jpg");

        var photoService = mock(PhotoService.class);
        when(photoService.recentForFeed(20)).thenReturn(List.of(photo));

        var controller = new FeedController(photoService);
        var response = controller.recentFeed(20);

        assertThat(response.getBody()).contains("A&amp;B &lt;test&gt;.jpg");
    }

    private Photo samplePhoto() {
        var photo = new Photo();
        photo.setId(1L);
        photo.setOriginalFilename("IMG_0001.jpg");
        photo.setInternalFilename("abc.jpg");
        photo.setUploadedAt(Instant.parse("2026-05-20T12:00:00Z"));
        return photo;
    }
}
