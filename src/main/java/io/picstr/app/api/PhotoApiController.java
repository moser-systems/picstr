package io.picstr.app.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.picstr.app.api.ApiDtos.BulkRequest;
import io.picstr.app.api.ApiDtos.PageDto;
import io.picstr.app.api.ApiDtos.PhotoDto;
import io.picstr.app.api.ApiDtos.UploadFailure;
import io.picstr.app.api.ApiDtos.UploadResult;
import io.picstr.app.form.PhotoSearchForm;
import io.picstr.app.form.PhotoUpdateForm;
import io.picstr.app.form.UploadForm;
import io.picstr.app.model.Photo;
import io.picstr.app.model.ThumbnailKeys;
import io.picstr.app.service.PhotoProcessingService;
import io.picstr.app.service.PhotoService;
import io.picstr.app.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
@Tag(name = "Photos")
public class PhotoApiController {

    static final int MAX_PAGE_SIZE = 100;

    private final PhotoService photoService;
    private final StorageService storageService;
    private final PhotoProcessingService processingService;

    public PhotoApiController(PhotoService photoService, StorageService storageService,
                              PhotoProcessingService processingService) {
        this.photoService = photoService;
        this.storageService = storageService;
        this.processingService = processingService;
    }

    @GetMapping
    @Operation(summary = "List or search active photos, newest first",
            description = "Without criteria all active photos are returned. q matches filename, description, category and tag names.")
    public PageDto<PhotoDto> list(@ModelAttribute PhotoSearchForm search,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return PageDto.of(photoService.search(search, Math.max(page, 0), clampSize(size)));
    }

    @GetMapping("/archived")
    @Operation(summary = "List archived photos, most recently archived first")
    public PageDto<PhotoDto> archived(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return PageDto.of(photoService.archived(Math.max(page, 0), clampSize(size)));
    }

    @GetMapping("/locations")
    @Operation(summary = "Locations of all active geotagged photos (as used by the gallery map)")
    public List<PhotoService.MapMarker> locations() {
        return photoService.mapMarkers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a photo (active or archived)")
    public PhotoDto get(@PathVariable Long id) {
        return PhotoDto.of(photoService.getAny(id));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Download the stored original")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id) {
        return stream(photoService.getAny(id), Photo::getInternalFilename);
    }

    @GetMapping("/{id}/thumbnail")
    @Operation(summary = "Download the JPEG thumbnail")
    public ResponseEntity<InputStreamResource> thumbnail(@PathVariable Long id) {
        return stream(photoService.getAny(id), Photo::getThumbnailKey);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload 1 to 20 photos",
            description = "Multipart fields: images (files), category (id or name), tags, description, latitude, longitude. "
                    + "Returns 201 if at least one photo was stored, 400 if none. New photos have status PROCESSING "
                    + "until HEIC conversion and the thumbnail are done in the background (poll GET /photos/{id}).")
    public ResponseEntity<UploadResult> upload(@Valid @ModelAttribute UploadForm form) {
        var images = form.nonEmptyImages();
        if (images.isEmpty() || images.size() > UploadForm.MAX_IMAGES) {
            throw new IllegalArgumentException("Upload 1 to " + UploadForm.MAX_IMAGES + " images");
        }
        var created = new ArrayList<PhotoDto>();
        var failed = new ArrayList<UploadFailure>();
        for (var image : images) {
            try {
                created.add(PhotoDto.of(photoService.upload(image, form)));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                failed.add(new UploadFailure(image.getOriginalFilename(), ex.getMessage()));
            }
        }
        var status = created.isEmpty() ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(new UploadResult(created, failed));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a photo's metadata (filename, description, category, coordinates, tags)")
    public PhotoDto update(@PathVariable Long id, @Valid @RequestBody PhotoUpdateForm form) {
        return PhotoDto.of(photoService.update(id, form));
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a photo")
    public void archive(@PathVariable Long id) {
        photoService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restore an archived photo")
    public void restore(@PathVariable Long id) {
        photoService.restore(id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Retry processing of a photo whose status is FAILED",
            description = "Sets the status back to PROCESSING and queues it; poll GET /photos/{id} for the result.")
    public void retry(@PathVariable Long id) {
        processingService.retry(id);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Run one action on up to 100 photos",
            description = "action: archive, restore, category (needs category), addTags or removeTags (need tags), "
                    + "retry (failed processing).")
    public PhotoService.BulkResult bulk(@RequestBody BulkRequest request) {
        var ids = request.ids();
        if (ids == null || ids.isEmpty() || ids.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Select 1 to " + MAX_PAGE_SIZE + " photos");
        }
        var action = request.action() == null ? "" : request.action();
        return switch (action) {
            case "archive" -> photoService.bulkArchive(ids);
            case "restore" -> photoService.bulkRestore(ids);
            case "category" -> photoService.bulkSetCategory(ids, request.category());
            case "addTags" -> photoService.bulkAddTags(ids, request.tags());
            case "removeTags" -> photoService.bulkRemoveTags(ids, request.tags());
            case "retry" -> processingService.retry(ids);
            default -> throw new IllegalArgumentException("Unknown bulk action: " + action);
        };
    }

    private ResponseEntity<InputStreamResource> stream(Photo photo, Function<Photo, String> key) {
        var object = storageService.get(key.apply(photo)).orElse(null);
        if (object == null && key.apply(photo).equals(photo.getThumbnailKey())) {
            // Not migrated yet (see ThumbnailKeyMigration)
            object = storageService.get(ThumbnailKeys.legacyForOriginal(photo.getInternalFilename())).orElse(null);
        }
        if (object == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                .contentLength(object.contentLength())
                .body(new InputStreamResource(object.content()));
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
