package io.picstr.app.api;

import java.util.List;

import io.picstr.app.api.ApiDtos.CategoryDto;
import io.picstr.app.form.CategoryForm;
import io.picstr.app.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
public class CategoryApiController {

    private final CategoryService service;

    public CategoryApiController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all categorys, sorted by name")
    public List<CategoryDto> list() {
        return service.list().stream().map(CategoryDto::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category")
    public CategoryDto get(@PathVariable Long id) {
        return CategoryDto.of(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a category", description = "name 2-100 characters (stored lower-case), color from the Tabler palette, optional description.")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryForm form) {
        var created = CategoryDto.of(service.create(form.getName(), form.getDescription(), form.getColor()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return CategoryDto.of(service.update(id, form.getName(), form.getDescription(), form.getColor()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a category", description = "409 if photos still use it.")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
