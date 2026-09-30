package io.picstr.app.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class NameLengthValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void categoryAndTagFormsShareTheSameMinimum() {
        var category = new CategoryForm();
        var tag = new TagForm();

        category.setName("a");
        tag.setName("a");
        assertThat(validator.validateProperty(category, "name")).hasSize(1);
        assertThat(validator.validateProperty(tag, "name")).hasSize(1);

        category.setName("uk");
        tag.setName("uk");
        assertThat(validator.validateProperty(category, "name")).isEmpty();
        assertThat(validator.validateProperty(tag, "name")).isEmpty();
    }
}
