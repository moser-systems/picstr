package io.picstr.app.service;

import java.util.List;
import java.util.Locale;

import io.picstr.app.model.Category;
import io.picstr.app.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CategoryService {

    private static final List<String> COLORS = List.of("blue", "azure", "indigo", "purple", "pink", "red", "orange", "yellow", "lime", "green", "teal", "cyan");

    @Autowired
    private CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categoryRepository.findAll().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Category> listPage(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return categoryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public Category create(String rawName, String rawDescription) {
        return create(rawName, rawDescription, "blue");
    }

    @Transactional
    public Category create(String rawName, String rawDescription, String rawColor) {
        var normalized = normalize(rawName);
        if (categoryRepository.findByNameIgnoreCase(normalized).isPresent()) {
            throw new IllegalArgumentException("Category already exists: " + normalized);
        }
        var category = new Category(normalized);
        category.setDescription(normalizeDescription(rawDescription));
        category.setColor(normalizeColor(rawColor));
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, String rawName, String rawDescription) {
        return update(id, rawName, rawDescription, null);
    }

    @Transactional
    public Category update(Long id, String rawName, String rawDescription, String rawColor) {
        var normalized = normalize(rawName);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(normalized, id)) {
            throw new IllegalArgumentException("Category already exists: " + normalized);
        }
        var category = get(id);
        category.setName(normalized);
        category.setDescription(normalizeDescription(rawDescription));
        if (StringUtils.hasText(rawColor)) {
            category.setColor(normalizeColor(rawColor));
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Category name is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public List<String> colors() {
        return COLORS;
    }

    private String normalizeColor(String value) {
        if (!StringUtils.hasText(value)) {
            return "blue";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid category color: " + normalized);
        }
        return normalized;
    }

}
