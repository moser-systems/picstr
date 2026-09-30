package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PhotoService photoService;

    private static final MockMultipartFile IMAGE = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1, 2, 3});

    @Test
    void upload_rejectsTooShortNewTagBeforeWritingToStorage() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category("other")));
        when(tagRepository.findByNameIgnoreCase("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.upload(IMAGE, form("1", List.of("x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name must be between 2 and 100 characters");

        verifyNoInteractions(storageService, thumbnailService, photoRepository);
    }

    @Test
    void upload_rejectsUnknownCategoryBeforeWritingToStorage() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.upload(IMAGE, form("99", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown category id: 99");

        verifyNoInteractions(storageService, thumbnailService, photoRepository);
    }

    @Test
    void upload_rejectsInvalidCoordinateBeforeWritingToStorage() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category("other")));
        var form = form("1", List.of());
        form.setLatitude("north");

        assertThatThrownBy(() -> photoService.upload(IMAGE, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid coordinate format");

        verifyNoInteractions(storageService, thumbnailService, photoRepository);
    }

    @Test
    void upload_storesTheOriginalAsUploadedAndQueuesProcessing() throws Exception {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category("other")));
        when(photoRepository.save(org.mockito.ArgumentMatchers.any(io.picstr.app.model.Photo.class))).thenAnswer(invocation -> {
            io.picstr.app.model.Photo photo = invocation.getArgument(0);
            photo.setId(42L);
            return photo;
        });
        var heic = new MockMultipartFile("images", "IMG_1.HEIC", "image/heic", tinyJpeg());

        var photo = photoService.upload(heic, form("1", List.of()));

        assertThat(photo.getProcessingStatus()).isEqualTo(io.picstr.app.model.ProcessingStatus.PROCESSING);
        assertThat(photo.getInternalFilename()).endsWith(".heic");
        assertThat(photo.getContentType()).isEqualTo("image/heic");
        org.mockito.Mockito.verify(storageService).upload(org.mockito.ArgumentMatchers.eq(photo.getInternalFilename()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq((long) heic.getSize()), org.mockito.ArgumentMatchers.eq("image/heic"));
        org.mockito.Mockito.verify(eventPublisher).publishEvent(new io.picstr.app.service.PhotoUploadedEvent(42L));
        verifyNoInteractions(thumbnailService);
    }

    /** Real image bytes, so metadata extraction works; the name and type claim HEIC to check nothing is converted. */
    private static byte[] tinyJpeg() throws java.io.IOException {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static UploadForm form(String category, List<String> tags) {
        var form = new UploadForm();
        form.setCategory(category);
        form.setTags(tags);
        return form;
    }
}
