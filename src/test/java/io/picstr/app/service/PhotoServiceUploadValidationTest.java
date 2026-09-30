package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import io.picstr.app.form.UploadForm;
import io.picstr.app.model.Category;
import io.picstr.app.repository.CategoryRepository;
import io.picstr.app.repository.PhotoRepository;
import io.picstr.app.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PhotoServiceUploadValidationTest {

    @Mock
    private StorageService storageService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private HeicHeifConversionService conversionService;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void upload_rejectsTooShortNewTagBeforeWritingToStorage() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category("other")));
        when(tagRepository.findByNameIgnoreCase("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.upload(form("1", List.of("x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name must be between 2 and 100 characters");

        verifyNoInteractions(storageService, thumbnailService, photoRepository);
    }

    @Test
    void upload_rejectsUnknownCategoryBeforeWritingToStorage() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.upload(form("99", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown category id: 99");

        verifyNoInteractions(storageService, thumbnailService, photoRepository);
    }

    private static UploadForm form(String category, List<String> tags) {
        var form = new UploadForm();
        form.setImage(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[] {1, 2, 3}));
        form.setCategory(category);
        form.setTags(tags);
        return form;
    }
}
