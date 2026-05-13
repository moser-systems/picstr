package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.picstr.app.model.Photo;
import io.picstr.app.service.PhotoService;
import io.picstr.app.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    @Mock
    private PhotoService photoService;

    @Mock
    private TagService tagService;

    @Test
    void byCategory_clampsPaginationAndAddsFilterAttributes() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);
        ReflectionTestUtils.setField(controller, "tagService", tagService);

        var model = new ExtendedModelMap();
        var redirects = new RedirectAttributesModelMap();
        var page = new PageImpl<>(List.of(new Photo()), PageRequest.of(0, 100), 1);
        when(photoService.byCategory("travel", 0, 100)).thenReturn(page);

        var view = controller.byCategory("travel", -2, 500, model, redirects);

        assertThat(view).isEqualTo("photo/list");
        verify(photoService).byCategory("travel", 0, 100);
        assertThat(model.getAttribute("photos")).isEqualTo(page.getContent());
        assertThat(model.getAttribute("pageData")).isEqualTo(page);
        assertThat(model.getAttribute("pageSize")).isEqualTo(100);
        assertThat(model.getAttribute("filterType")).isEqualTo("category");
        assertThat(model.getAttribute("filterValue")).isEqualTo("travel");
    }

    @Test
    void byTag_redirectsToRootOnValidationError() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);
        ReflectionTestUtils.setField(controller, "tagService", tagService);

        var model = new ExtendedModelMap();
        var redirects = new RedirectAttributesModelMap();
        when(photoService.byTag("", 0, 5)).thenThrow(new IllegalArgumentException("Tag is required"));

        var view = controller.byTag("", 0, 5, model, redirects);

        assertThat(view).isEqualTo("redirect:/");
        assertThat(redirects.getFlashAttributes()).containsKey("error");
        assertThat(redirects.getFlashAttributes().get("error")).isEqualTo("Tag is required");
    }
}
