package io.picstr.app.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class TagLimitValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void uploadForm_rejectsMoreThanFiveTags() {
        var form = new UploadForm();
        form.setTags(List.of("a", "b", "c", "d", "e", "f"));

        assertThat(validator.validateProperty(form, "tags")).hasSize(1);
    }

    @Test
    void uploadForm_acceptsFiveTags() {
        var form = new UploadForm();
        form.setTags(List.of("a", "b", "c", "d", "e"));

        assertThat(validator.validateProperty(form, "tags")).isEmpty();
    }

    @Test
    void photoUpdateForm_rejectsMoreThanFiveTags() {
        var form = new PhotoUpdateForm();
        form.setTags(List.of("a", "b", "c", "d", "e", "f"));

        assertThat(validator.validateProperty(form, "tags")).hasSize(1);
    }
}
