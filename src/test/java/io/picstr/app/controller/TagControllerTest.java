package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.picstr.app.model.Tag;
import io.picstr.app.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private TagService tagService;

    @Test
    void list_clampsPageAndSizeAndPopulatesPaginationModel() {
        var controller = new TagController(tagService);
        var model = new ExtendedModelMap();

        var tag = new Tag("family");
        tag.setColor("teal");
        var page = new PageImpl<>(List.of(tag), PageRequest.of(0, 100), 1);
        when(tagService.listPage(0, 100)).thenReturn(page);

        var view = controller.list(-1, 101, model);

        assertThat(view).isEqualTo("tag/list");
        verify(tagService).listPage(0, 100);
        assertThat(model.getAttribute("tags")).isEqualTo(page.getContent());
        assertThat(model.getAttribute("pageData")).isEqualTo(page);
        assertThat(model.getAttribute("pageSize")).isEqualTo(100);
    }
}
