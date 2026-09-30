package io.picstr.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import io.picstr.app.form.UploadForm;
import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.service.PhotoService;
import io.picstr.app.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PhotoApiControllerTest {

    @Mock
    private PhotoService photoService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PhotoApiController controller;

    @Test
    void upload_returnsCreatedWithFailuresListed() {
        var ok = image("a.jpg");
        var broken = image("b.jpg");
        var form = form(ok, broken);
        when(photoService.upload(ok, form)).thenReturn(photo(7L, "a.jpg"));
        lenient().doThrow(new IllegalStateException("Could not upload image")).when(photoService).upload(broken, form);

        var response = controller.upload(form);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().created()).extracting(ApiDtos.PhotoDto::id).containsExactly(7L);
        assertThat(response.getBody().failed()).containsExactly(new ApiDtos.UploadFailure("b.jpg", "Could not upload image"));
    }

    @Test
    void upload_returnsBadRequestWhenNothingWasStored() {
        var broken = image("b.jpg");
        var form = form(broken);
        doThrow(new IllegalArgumentException("Unknown category id: 9")).when(photoService).upload(broken, form);

        assertThat(controller.upload(form).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void upload_requiresOneToTwentyImages() {
        assertThatThrownBy(() -> controller.upload(form())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(photoService);
    }

    @Test
    void bulk_validatesSelectionAndAction() {
        assertThatThrownBy(() -> controller.bulk(new ApiDtos.BulkRequest(List.of(), "archive", null, null)))
                .hasMessage("Select 1 to 100 photos");
        assertThatThrownBy(() -> controller.bulk(new ApiDtos.BulkRequest(List.of(1L), null, null, null)))
                .hasMessage("Unknown bulk action: ");
        verifyNoInteractions(photoService);
    }

    @Test
    void photoDtoLinksToApiFileEndpoints() {
        var dto = ApiDtos.PhotoDto.of(photo(3L, "x.png"));

        assertThat(dto.fileUrl()).isEqualTo("/api/v1/photos/3/file");
        assertThat(dto.thumbnailUrl()).isEqualTo("/api/v1/photos/3/thumbnail");
        assertThat(dto.archived()).isFalse();
    }

    private static Photo photo(long id, String name) {
        var photo = new Photo();
        photo.setId(id);
        photo.setOriginalFilename(name);
        photo.setInternalFilename(name);
        photo.setCategory(new Category("other"));
        return photo;
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1});
    }

    private static UploadForm form(MockMultipartFile... images) {
        var form = new UploadForm();
        form.setCategory("1");
        form.setImages(List.of(images));
        return form;
    }
}
