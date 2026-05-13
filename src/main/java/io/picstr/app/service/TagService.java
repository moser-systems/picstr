package io.picstr.app.service;

import java.util.List;
import java.util.Locale;

import io.picstr.app.model.Tag;
import io.picstr.app.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TagService {

    private static final List<String> COLORS = List.of("blue", "azure", "indigo", "purple", "pink", "red", "orange", "yellow", "lime", "green", "teal", "cyan");

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> list() {
        return tagRepository.findAll().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Tag> listPage(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return tagRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Tag get(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + id));
    }

    @Transactional
    public Tag create(String rawName, String rawDescription) {
        return create(rawName, rawDescription, "blue");
    }

    @Transactional
    public Tag create(String rawName, String rawDescription, String rawColor) {
        var normalized = normalize(rawName);
        if (tagRepository.findByNameIgnoreCase(normalized).isPresent()) {
            throw new IllegalArgumentException("Tag already exists: " + normalized);
        }
        var tag = new Tag(normalized);
        tag.setDescription(normalizeDescription(rawDescription));
        tag.setColor(normalizeColor(rawColor));
        return tagRepository.save(tag);
    }

    @Transactional
    public Tag update(Long id, String rawName, String rawDescription) {
        return update(id, rawName, rawDescription, null);
    }

    @Transactional
    public Tag update(Long id, String rawName, String rawDescription, String rawColor) {
        var normalized = normalize(rawName);
        if (tagRepository.existsByNameIgnoreCaseAndIdNot(normalized, id)) {
            throw new IllegalArgumentException("Tag already exists: " + normalized);
        }
        var tag = get(id);
        tag.setName(normalized);
        tag.setDescription(normalizeDescription(rawDescription));
        if (StringUtils.hasText(rawColor)) {
            tag.setColor(normalizeColor(rawColor));
        }
        return tagRepository.save(tag);
    }

    @Transactional
    public void delete(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new IllegalArgumentException("Tag not found: " + id);
        }
        tagRepository.deleteById(id);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Tag name is required");
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
            throw new IllegalArgumentException("Invalid tag color: " + normalized);
        }
        return normalized;
    }
}
