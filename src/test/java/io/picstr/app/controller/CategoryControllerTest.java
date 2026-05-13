package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.picstr.app.model.Category;
import io.picstr.app.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @Test
    void list_clampsPageAndSizeAndPopulatesPaginationModel() {
        var controller = new CategoryController(categoryService);
        var model = new ExtendedModelMap();

        var category = new Category("other");
        category.setColor("blue");
        var page = new PageImpl<>(List.of(category), PageRequest.of(0, 100), 1);
        when(categoryService.listPage(0, 100)).thenReturn(page);

        var view = controller.list(-5, 999, model);

        assertThat(view).isEqualTo("category/list");
        verify(categoryService).listPage(0, 100);
        assertThat(model.getAttribute("categories")).isEqualTo(page.getContent());
        assertThat(model.getAttribute("pageData")).isEqualTo(page);
        assertThat(model.getAttribute("pageSize")).isEqualTo(100);
    }
}
