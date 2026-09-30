package io.picstr.app.api;

import java.util.List;

import io.picstr.app.api.ApiDtos.TagDto;
import io.picstr.app.form.TagForm;
import io.picstr.app.service.TagService;
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
@RequestMapping("/api/v1/tags")
@Tag(name = "Tags")
public class TagApiController {

    private final TagService service;

    public TagApiController(TagService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all tags, sorted by name")
    public List<TagDto> list() {
        return service.list().stream().map(TagDto::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tag")
    public TagDto get(@PathVariable Long id) {
        return TagDto.of(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a tag", description = "name 2-100 characters (stored lower-case), color from the Tabler palette, optional description.")
    public ResponseEntity<TagDto> create(@Valid @RequestBody TagForm form) {
        var created = TagDto.of(service.create(form.getName(), form.getDescription(), form.getColor()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tag")
    public TagDto update(@PathVariable Long id, @Valid @RequestBody TagForm form) {
        return TagDto.of(service.update(id, form.getName(), form.getDescription(), form.getColor()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag", description = "409 if photos still use it.")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
