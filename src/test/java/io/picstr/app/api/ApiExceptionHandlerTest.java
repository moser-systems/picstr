package io.picstr.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.picstr.app.service.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsServiceErrorsToStatusCodes() {
        assertThat(handler.notFound(new NotFoundException("Photo not found: 1")).getStatus()).isEqualTo(404);
        assertThat(handler.badRequest(new IllegalArgumentException("bad")).getStatus()).isEqualTo(400);
        assertThat(handler.conflict(new DataIntegrityViolationException("fk")).getStatus()).isEqualTo(409);
    }

    @Test
    void listsFieldErrors() {
        var result = new BeanPropertyBindingResult(new Object(), "form");
        result.rejectValue(null, "x", "global problem");
        var bind = new BindException(result);
        bind.addError(new org.springframework.validation.FieldError("form", "name", "size must be between 2 and 100"));

        var problem = handler.invalid(bind);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties().get("errors").toString())
                .contains("name=size must be between 2 and 100").contains("form=global problem");
    }
}
