package io.picstr.app.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.picstr.app.form.PhotoSearchForm;
import io.picstr.app.form.PhotoUpdateForm;
import io.picstr.app.form.UploadForm;
import io.picstr.app.model.Photo;
import io.picstr.app.model.Tag;
import io.picstr.app.service.PhotoService;
import io.picstr.app.service.TagService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/photos")
public class PhotoController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private PhotoService service;

    @Autowired
    private TagService tagService;

    @Autowired
    private MessageSource messageSource;

    @GetMapping("")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "12") int size,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            var safePage = Math.max(page, 0);
            var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            var photosPage = service.list(safePage, safeSize);
            model.addAttribute("photos", photosPage.getContent());
            model.addAttribute("pageData", photosPage);
            model.addAttribute("pageSize", photosPage.getSize());
            addBulkOptions(model);
            return "photo/list";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    /** Upper limit for one bulk action; matches the largest page size. */
    static final int MAX_BULK_PHOTOS = MAX_PAGE_SIZE;

    @PostMapping("/bulk")
    public String bulk(@RequestParam(name = "ids", required = false) List<Long> ids,
                       @RequestParam String action,
                       @RequestParam(required = false) String category,
                       @RequestParam(name = "tags", required = false) List<String> tags,
                       @RequestParam(defaultValue = "/photos") String returnTo,
                       RedirectAttributes redirectAttributes,
                       Locale locale) {
        var target = "redirect:" + (isLocalPath(returnTo) ? returnTo : "/photos");
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "msg.bulk.noSelection");
            return target;
        }
        if (ids.size() > MAX_BULK_PHOTOS) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("msg.bulk.tooMany", new Object[] {MAX_BULK_PHOTOS}, locale));
            return target;
        }
        try {
            var result = switch (action) {
                case "archive" -> service.bulkArchive(ids);
                case "restore" -> service.bulkRestore(ids);
                case "category" -> service.bulkSetCategory(ids, category);
                case "addTags" -> service.bulkAddTags(ids, tags);
                case "removeTags" -> service.bulkRemoveTags(ids, tags);
                default -> throw new IllegalArgumentException("Unknown bulk action: " + action);
            };
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("msg.bulk." + action, new Object[] {result.changed()}, locale));
            if (result.skipped() > 0) {
                redirectAttributes.addFlashAttribute("warning", messageSource.getMessage("msg.bulk.tagLimit",
                        new Object[] {result.skipped(), Photo.MAX_TAGS}, locale));
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return target;
    }

    @GetMapping("/search")
    public String search(@ModelAttribute("search") PhotoSearchForm search,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "12") int size,
                         Model model) {
        addBulkOptions(model);
        if (!search.isEmpty()) {
            var safePage = Math.max(page, 0);
            var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            try {
                var photosPage = service.search(search, safePage, safeSize);
                model.addAttribute("photos", photosPage.getContent());
                model.addAttribute("pageData", photosPage);
                model.addAttribute("pageSize", photosPage.getSize());
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        return "photo/search";
    }

    @GetMapping("/map")
    public String map() {
        return "photo/map";
    }

    @GetMapping(value = "/map/markers", produces = "application/json")
    @ResponseBody
    public List<PhotoService.MapMarker> mapMarkers() {
        return service.mapMarkers();
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        if (!model.containsAttribute("uploadForm")) {
            model.addAttribute("uploadForm", new UploadForm());
        }
        model.addAttribute("categories", service.categories());
        model.addAttribute("allTags", tagService.list());
        return "photo/upload-form";
    }

    @PostMapping("/upload")
    public String upload(@Valid @ModelAttribute("uploadForm") UploadForm uploadForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes,
                         Locale locale) {
        var images = uploadForm.nonEmptyImages();
        if (images.isEmpty()) {
            bindingResult.rejectValue("images", "msg.photo.images.required", "Choose at least one photo.");
        } else if (images.size() > UploadForm.MAX_IMAGES) {
            bindingResult.rejectValue("images", "msg.photo.images.max", new Object[] {UploadForm.MAX_IMAGES},
                    "Too many photos.");
        }

        if (!bindingResult.hasErrors()) {
            // Each image is stored in its own transaction, so one bad file doesn't undo the others.
            var failures = new ArrayList<String>();
            for (var image : images) {
                try {
                    service.upload(image, uploadForm);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    log.error("Failed to upload image {}", image.getOriginalFilename(), ex);
                    failures.add(image.getOriginalFilename() + ": " + ex.getMessage());
                }
            }
            var uploaded = images.size() - failures.size();
            if (uploaded > 0) {
                // Keep category and tags for the next capture; everything else is per photo.
                var nextForm = new UploadForm();
                nextForm.setCategory(uploadForm.getCategory());
                nextForm.setTags(uploadForm.getTags());
                redirectAttributes.addFlashAttribute("uploadForm", nextForm);
                redirectAttributes.addFlashAttribute("success", images.size() == 1
                        ? "msg.photo.upload.success"
                        : messageSource.getMessage("msg.photo.upload.successMany", new Object[] {uploaded}, locale));
                if (!failures.isEmpty()) {
                    redirectAttributes.addFlashAttribute("warning", messageSource.getMessage("msg.photo.upload.partial",
                            new Object[] {failures.size(), String.join("; ", failures)}, locale));
                }
                return "redirect:/photos/upload";
            }
            failures.forEach(failure -> bindingResult.reject("upload.failed", failure));
        }
        model.addAttribute("categories", service.categories());
        model.addAttribute("allTags", tagService.list());
        return "photo/upload-form";
    }

    /**
     * Polled by gallery cards of photos that are still processing: 204 (no change) while processing, then
     * the finished card, which replaces the placeholder and stops the polling.
     */
    @GetMapping("/{id}/card")
    public String card(@PathVariable Long id, Model model, HttpServletResponse response) {
        var photo = service.getAny(id);
        if (photo.isProcessing()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }
        model.addAttribute("photo", photo);
        model.addAttribute("bulkEnabled", true);
        return "photo/list :: photoCard(photo=${photo})";
    }

    /** Polled by the detail page while processing: 204, then 200 with HX-Refresh so htmx reloads the page. */
    @GetMapping("/{id}/processed")
    public ResponseEntity<Void> processed(@PathVariable Long id) {
        if (service.getAny(id).isProcessing()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().header("HX-Refresh", "true").build();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("photo", service.get(id));
            return "photo/detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var photo = service.get(id);

            var form = new PhotoUpdateForm();
            form.setOriginalFilename(photo.getOriginalFilename());
            form.setDescription(photo.getDescription());
            form.setCategory(String.valueOf(photo.getCategory().getId()));
            form.setTags(photo.getTags().stream().map(Tag::getName).toList());
            form.setLatitude(photo.getLatitude() == null ? null : photo.getLatitude().toPlainString());
            form.setLongitude(photo.getLongitude() == null ? null : photo.getLongitude().toPlainString());

            model.addAttribute("photoId", id);
            model.addAttribute("photoUpdateForm", form);
            model.addAttribute("categories", service.categories());
            model.addAttribute("allTags", tagService.list());
            return "photo/edit-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/by-category/{category}")
    public String byCategory(@PathVariable String category,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "12") int size,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            var safePage = Math.max(page, 0);
            var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            var photosPage = service.byCategory(category, safePage, safeSize);
            model.addAttribute("photos", photosPage.getContent());
            model.addAttribute("pageData", photosPage);
            model.addAttribute("pageSize", photosPage.getSize());
            addBulkOptions(model);
            model.addAttribute("filterType", "category");
            model.addAttribute("filterValue", category);
            return "photo/list";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/by-tag/{tag}")
    public String byTag(@PathVariable String tag,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "12") int size,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        try {
            var safePage = Math.max(page, 0);
            var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            var photosPage = service.byTag(tag, safePage, safeSize);
            model.addAttribute("photos", photosPage.getContent());
            model.addAttribute("pageData", photosPage);
            model.addAttribute("pageSize", photosPage.getSize());
            addBulkOptions(model);
            model.addAttribute("filterType", "tag");
            model.addAttribute("filterValue", tag);
            return "photo/list";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/archive")
    public String archive(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          Model model) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        var photosPage = service.archived(safePage, safeSize);
        model.addAttribute("photos", photosPage.getContent());
        model.addAttribute("pageData", photosPage);
        model.addAttribute("pageSize", photosPage.getSize());
        return "photo/archive-list";
    }

    @GetMapping("/archive/{id}")
    public String archivedDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("photo", service.getArchived(id));
            model.addAttribute("archived", true);
            return "photo/detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/photos/archive";
        }
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id,
                          @RequestParam(defaultValue = "/photos/archive") String redirect,
                          RedirectAttributes redirectAttributes) {
        try {
            service.restore(id);
            redirectAttributes.addFlashAttribute("success", "msg.photo.restore.success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + (isLocalPath(redirect) ? redirect : "/photos/archive");
    }

    /** Categories and tags for the bulk action bar (and the search form). */
    private void addBulkOptions(Model model) {
        model.addAttribute("bulkEnabled", true);
        model.addAttribute("categories", service.categories());
        model.addAttribute("allTags", tagService.list());
    }

    /** Only allow redirects to paths on this application, never to another host. */
    static boolean isLocalPath(String target) {
        return target != null
                && target.startsWith("/")
                && !target.startsWith("//")
                && !target.startsWith("/\\");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("photoUpdateForm") PhotoUpdateForm photoUpdateForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        model.addAttribute("photoId", id);
        model.addAttribute("categories", service.categories());
        model.addAttribute("allTags", tagService.list());
        if (bindingResult.hasErrors()) {
            return "photo/edit-form";
        }

        try {
            service.update(id, photoUpdateForm);
            redirectAttributes.addFlashAttribute("success", "msg.photo.update.success");
            return "redirect:/photos/" + id;
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            return "photo/edit-form";
        }
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            service.archive(id);
            redirectAttributes.addFlashAttribute("success", "msg.photo.archive.success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }
}
