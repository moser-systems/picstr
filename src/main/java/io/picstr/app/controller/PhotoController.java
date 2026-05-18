package io.picstr.app.controller;

import io.picstr.app.form.PhotoUpdateForm;
import io.picstr.app.form.UploadForm;
import io.picstr.app.model.Tag;
import io.picstr.app.service.PhotoService;
import io.picstr.app.service.TagService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
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
            return "photo/list";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
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
                         Model model) {
        model.addAttribute("categories", service.categories());
        model.addAttribute("allTags", tagService.list());

        if (bindingResult.hasErrors()) {
            model.addAttribute("uploadForm", uploadForm);
            return "photo/upload-form";
        }

        try {
            service.upload(uploadForm);
            uploadForm.setImage(null);
            model.addAttribute("uploadForm", uploadForm);
            model.addAttribute("success", "msg.photo.upload.success");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.error("Failed to upload image", ex);
            bindingResult.reject("upload.failed", ex.getMessage());
        }
        return "photo/upload-form";
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
                             @RequestParam(defaultValue = "5") int size,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            var safePage = Math.max(page, 0);
            var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            var photosPage = service.byCategory(category, safePage, safeSize);
            model.addAttribute("photos", photosPage.getContent());
            model.addAttribute("pageData", photosPage);
            model.addAttribute("pageSize", photosPage.getSize());
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
        return "redirect:" + redirect;
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
