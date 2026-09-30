package io.picstr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import io.picstr.app.form.UploadForm;
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
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
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

    @Test
    void upload_redirectsAfterSuccessAndKeepsCategoryAndTags() {
        var controller = uploadController();
        var image = image("a.jpg");
        var form = uploadForm(image);
        form.setDescription("sunset");
        var redirects = new RedirectAttributesModelMap();

        var view = controller.upload(form, bindingResult(form), new ExtendedModelMap(), redirects, Locale.ENGLISH);

        assertThat(view).isEqualTo("redirect:/photos/upload");
        verify(photoService).upload(image, form);
        var nextForm = (UploadForm) redirects.getFlashAttributes().get("uploadForm");
        assertThat(nextForm.getCategory()).isEqualTo("travel");
        assertThat(nextForm.getTags()).containsExactly("beach");
        assertThat(nextForm.getDescription()).isNull();
        assertThat(nextForm.getImages()).isEmpty();
        assertThat(redirects.getFlashAttributes().get("success")).isEqualTo("msg.photo.upload.success");
    }

    @Test
    void upload_storesEveryImageAndReportsPartialFailures() {
        var controller = uploadController();
        var first = image("a.jpg");
        var broken = image("broken.jpg");
        var third = image("c.jpg");
        var form = uploadForm(first, broken, third);
        lenient().doThrow(new IllegalStateException("Could not upload image")).when(photoService).upload(broken, form);
        var redirects = new RedirectAttributesModelMap();

        var view = controller.upload(form, bindingResult(form), new ExtendedModelMap(), redirects, Locale.ENGLISH);

        assertThat(view).isEqualTo("redirect:/photos/upload");
        verify(photoService).upload(first, form);
        verify(photoService).upload(third, form);
        assertThat(redirects.getFlashAttributes().get("success")).isEqualTo("2 photos uploaded.");
        assertThat(redirects.getFlashAttributes().get("warning"))
                .isEqualTo("1 photo(s) could not be uploaded: broken.jpg: Could not upload image");
    }

    @Test
    void upload_showsFormWithErrorsWhenEveryImageFails() {
        var controller = uploadController();
        var image = image("a.jpg");
        var form = uploadForm(image);
        doThrow(new IllegalArgumentException("Unknown category id: 9")).when(photoService).upload(image, form);
        var bindingResult = bindingResult(form);

        var view = controller.upload(form, bindingResult, new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        assertThat(view).isEqualTo("photo/upload-form");
        assertThat(bindingResult.getGlobalErrors()).singleElement()
                .satisfies(error -> assertThat(error.getDefaultMessage()).isEqualTo("a.jpg: Unknown category id: 9"));
    }

    @Test
    void upload_requiresAtLeastOneImage() {
        var controller = uploadController();
        var form = uploadForm(new MockMultipartFile("images", "", "application/octet-stream", new byte[0]));
        var bindingResult = bindingResult(form);

        var view = controller.upload(form, bindingResult, new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        assertThat(view).isEqualTo("photo/upload-form");
        assertThat(bindingResult.getFieldError("images").getCode()).isEqualTo("msg.photo.images.required");
        verify(photoService, never()).upload(any(), any());
    }

    @Test
    void upload_rejectsMoreThanTheMaximumNumberOfImages() {
        var controller = uploadController();
        var images = new MockMultipartFile[UploadForm.MAX_IMAGES + 1];
        for (int i = 0; i < images.length; i++) {
            images[i] = image("img" + i + ".jpg");
        }
        var form = uploadForm(images);
        var bindingResult = bindingResult(form);

        var view = controller.upload(form, bindingResult, new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        assertThat(view).isEqualTo("photo/upload-form");
        assertThat(bindingResult.getFieldError("images").getCode()).isEqualTo("msg.photo.images.max");
        verify(photoService, never()).upload(any(), any());
    }

    @Test
    void search_showsOnlyTheFormWithoutCriteria() {
        var controller = uploadController();
        var model = new ExtendedModelMap();

        var view = controller.search(new io.picstr.app.form.PhotoSearchForm(), 0, 12, model);

        assertThat(view).isEqualTo("photo/search");
        assertThat(model.getAttribute("pageData")).isNull();
        verify(photoService, never()).search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void search_clampsPagingAndAddsResults() {
        var controller = uploadController();
        var search = new io.picstr.app.form.PhotoSearchForm();
        search.setQ("beach");
        var page = new PageImpl<>(List.of(new Photo()), PageRequest.of(0, 100), 1);
        when(photoService.search(search, 0, 100)).thenReturn(page);
        var model = new ExtendedModelMap();

        controller.search(search, -1, 500, model);

        assertThat(model.getAttribute("photos")).isEqualTo(page.getContent());
        assertThat(model.getAttribute("pageData")).isEqualTo(page);
    }

    @Test
    void bulk_runsActionAndReportsResult() {
        var controller = uploadController();
        when(photoService.bulkAddTags(List.of(1L, 2L), List.of("beach")))
                .thenReturn(new PhotoService.BulkResult(1, 1));
        var redirects = new RedirectAttributesModelMap();

        var view = controller.bulk(List.of(1L, 2L), "addTags", null, List.of("beach"), "/photos/search?q=x",
                redirects, Locale.ENGLISH);

        assertThat(view).isEqualTo("redirect:/photos/search?q=x");
        assertThat(redirects.getFlashAttributes().get("success")).isEqualTo("Tags added to 1 photos.");
        assertThat(redirects.getFlashAttributes().get("warning"))
                .isEqualTo("1 photo(s) skipped because they would have more than 5 tags.");
    }

    @Test
    void bulk_ignoresExternalReturnUrl() {
        var controller = uploadController();
        when(photoService.bulkArchive(List.of(1L))).thenReturn(new PhotoService.BulkResult(1, 0));

        var view = controller.bulk(List.of(1L), "archive", null, null, "https://evil.example",
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        assertThat(view).isEqualTo("redirect:/photos");
    }

    @Test
    void bulk_requiresSelectionAndKnownActionWithinLimit() {
        var controller = uploadController();

        var none = new RedirectAttributesModelMap();
        controller.bulk(List.of(), "archive", null, null, "/photos", none, Locale.ENGLISH);
        assertThat(none.getFlashAttributes().get("error")).isEqualTo("msg.bulk.noSelection");

        var tooMany = new RedirectAttributesModelMap();
        var ids = java.util.stream.LongStream.rangeClosed(1, PhotoController.MAX_BULK_PHOTOS + 1).boxed().toList();
        controller.bulk(ids, "archive", null, null, "/photos", tooMany, Locale.ENGLISH);
        assertThat(tooMany.getFlashAttributes().get("error")).isEqualTo("Please select at most 100 photos.");

        var unknown = new RedirectAttributesModelMap();
        controller.bulk(List.of(1L), "delete", null, null, "/photos", unknown, Locale.ENGLISH);
        assertThat(unknown.getFlashAttributes().get("error")).isEqualTo("Unknown bulk action: delete");
        verifyNoInteractions(photoService);
    }

    private PhotoController uploadController() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);
        ReflectionTestUtils.setField(controller, "tagService", tagService);
        var messages = new StaticMessageSource();
        messages.addMessage("msg.photo.upload.successMany", Locale.ENGLISH, "{0} photos uploaded.");
        messages.addMessage("msg.photo.upload.partial", Locale.ENGLISH, "{0} photo(s) could not be uploaded: {1}");
        messages.addMessage("msg.bulk.addTags", Locale.ENGLISH, "Tags added to {0} photos.");
        messages.addMessage("msg.bulk.archive", Locale.ENGLISH, "{0} photos archived.");
        messages.addMessage("msg.bulk.tagLimit", Locale.ENGLISH, "{0} photo(s) skipped because they would have more than {1} tags.");
        messages.addMessage("msg.bulk.tooMany", Locale.ENGLISH, "Please select at most {0} photos.");
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        return controller;
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1, 2, 3});
    }

    private static UploadForm uploadForm(MockMultipartFile... images) {
        var form = new UploadForm();
        form.setCategory("travel");
        form.setTags(List.of("beach"));
        form.setImages(List.of(images));
        return form;
    }

    private static BeanPropertyBindingResult bindingResult(UploadForm form) {
        return new BeanPropertyBindingResult(form, "uploadForm");
    }

    @Test
    void restore_ignoresRedirectToOtherHost() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);

        var view = controller.restore(7L, "https://evil.example/phish", new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/photos/archive");
        verify(photoService).restore(7L);
    }

    @Test
    void restore_followsLocalRedirect() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);

        var view = controller.restore(7L, "/photos/7", new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/photos/7");
    }

    @Test
    void isLocalPath_rejectsProtocolRelativeAndBackslashTargets() {
        assertThat(PhotoController.isLocalPath("/photos/archive")).isTrue();
        assertThat(PhotoController.isLocalPath("//evil.example")).isFalse();
        assertThat(PhotoController.isLocalPath("/\\evil.example")).isFalse();
        assertThat(PhotoController.isLocalPath("https://evil.example")).isFalse();
        assertThat(PhotoController.isLocalPath(null)).isFalse();
    }

    @Test
    void mapMarkers_returnsMarkersFromService() {
        var controller = new PhotoController();
        ReflectionTestUtils.setField(controller, "service", photoService);
        var markers = List.of(new PhotoService.MapMarker(1L, 47.0, 8.0, "IMG_1.jpg", "/assets/thumb_a.jpg"));
        when(photoService.mapMarkers()).thenReturn(markers);

        assertThat(controller.map()).isEqualTo("photo/map");
        assertThat(controller.mapMarkers()).isEqualTo(markers);
    }
}
