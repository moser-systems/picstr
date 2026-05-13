package io.picstr.app.controller;

import io.picstr.app.form.CategoryForm;
import io.picstr.app.service.CategoryService;
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
@RequestMapping("/categories")
public class CategoryController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        var categoriesPage = categoryService.listPage(safePage, safeSize);
        model.addAttribute("categories", categoriesPage.getContent());
        model.addAttribute("pageData", categoriesPage);
        model.addAttribute("pageSize", categoriesPage.getSize());
        return "category/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("category", categoryService.get(id));
            return "category/detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/categories";
        }
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new CategoryForm());
        }
        model.addAttribute("colors", categoryService.colors());
        model.addAttribute("isEdit", false);
        return "category/form";
    }

    @PostMapping("")
    public String create(@Valid @ModelAttribute("categoryForm") CategoryForm categoryForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("colors", categoryService.colors());
            model.addAttribute("isEdit", false);
            return "category/form";
        }

        try {
            categoryService.create(categoryForm.getName(), categoryForm.getDescription(), categoryForm.getColor());
            redirectAttributes.addFlashAttribute("success", "msg.category.create.success");
            return "redirect:/categories";
        } catch (IllegalArgumentException ex) {
            rejectFieldError(bindingResult, ex);
            model.addAttribute("colors", categoryService.colors());
            model.addAttribute("isEdit", false);
            return "category/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var category = categoryService.get(id);

            var form = new CategoryForm();
            form.setName(category.getName());
            form.setDescription(category.getDescription());
            form.setColor(category.getColor());

            model.addAttribute("categoryForm", form);
            model.addAttribute("categoryId", id);
            model.addAttribute("colors", categoryService.colors());
            model.addAttribute("isEdit", true);
            return "category/form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/categories";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("categoryForm") CategoryForm categoryForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("colors", categoryService.colors());
            model.addAttribute("categoryId", id);
            model.addAttribute("isEdit", true);
            return "category/form";
        }

        try {
            categoryService.update(id, categoryForm.getName(), categoryForm.getDescription(), categoryForm.getColor());
            redirectAttributes.addFlashAttribute("success", "msg.category.update.success");
            return "redirect:/categories";
        } catch (IllegalArgumentException ex) {
            rejectFieldError(bindingResult, ex);
            model.addAttribute("colors", categoryService.colors());
            model.addAttribute("categoryId", id);
            model.addAttribute("isEdit", true);
            return "category/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("success", "msg.category.delete.success");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "msg.category.delete.failed.stillUsed");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    private void rejectFieldError(BindingResult bindingResult, IllegalArgumentException ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        var field = message.contains("color") ? "color" : "name";
        bindingResult.rejectValue(field, "category." + field, ex.getMessage());
    }
}
