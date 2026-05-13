package io.picstr.app.controller;

import io.picstr.app.form.TagForm;
import io.picstr.app.service.TagService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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

@Controller
@RequestMapping("/tags")
public class TagController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        var tagsPage = tagService.listPage(safePage, safeSize);
        model.addAttribute("tags", tagsPage.getContent());
        model.addAttribute("pageData", tagsPage);
        model.addAttribute("pageSize", tagsPage.getSize());
        return "tag/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("tag", tagService.get(id));
            return "tag/detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tags";
        }
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("tagForm")) {
            model.addAttribute("tagForm", new TagForm());
        }
        model.addAttribute("colors", tagService.colors());
        model.addAttribute("isEdit", false);
        return "tag/form";
    }

    @PostMapping("")
    public String create(@Valid @ModelAttribute("tagForm") TagForm tagForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("colors", tagService.colors());
            model.addAttribute("isEdit", false);
            return "tag/form";
        }

        try {
            tagService.create(tagForm.getName(), tagForm.getDescription(), tagForm.getColor());
            redirectAttributes.addFlashAttribute("message", "Tag created.");
            return "redirect:/tags";
        } catch (IllegalArgumentException ex) {
            rejectFieldError(bindingResult, ex);
            model.addAttribute("colors", tagService.colors());
            model.addAttribute("isEdit", false);
            return "tag/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var tag = tagService.get(id);
            var form = new TagForm();
            form.setName(tag.getName());
            form.setDescription(tag.getDescription());
            form.setColor(tag.getColor());
            model.addAttribute("tagForm", form);
            model.addAttribute("tagId", id);
            model.addAttribute("colors", tagService.colors());
            model.addAttribute("isEdit", true);
            return "tag/form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tags";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("tagForm") TagForm tagForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("colors", tagService.colors());
            model.addAttribute("tagId", id);
            model.addAttribute("isEdit", true);
            return "tag/form";
        }

        try {
            tagService.update(id, tagForm.getName(), tagForm.getDescription(), tagForm.getColor());
            redirectAttributes.addFlashAttribute("success", "msg.tag.update.success");
            return "redirect:/tags";
        } catch (IllegalArgumentException ex) {
            rejectFieldError(bindingResult, ex);
            model.addAttribute("colors", tagService.colors());
            model.addAttribute("tagId", id);
            model.addAttribute("isEdit", true);
            return "tag/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tagService.delete(id);
            redirectAttributes.addFlashAttribute("success", "msg.tag.delete.success");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "msg.tag.delete.error.stillUsed");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tags";
    }

    private void rejectFieldError(BindingResult bindingResult, IllegalArgumentException ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        var field = message.contains("color") ? "color" : "name";
        bindingResult.rejectValue(field, "tag." + field, ex.getMessage());
    }
}
