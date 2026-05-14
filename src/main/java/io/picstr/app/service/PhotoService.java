package io.picstr.app.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import io.picstr.app.form.UploadForm;
import io.picstr.app.form.PhotoUpdateForm;
import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.model.Tag;
import io.picstr.app.repository.CategoryRepository;
import io.picstr.app.repository.PhotoRepository;
import io.picstr.app.repository.TagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PhotoService {

    private static final String THUMBNAIL_KEY_PREFIX = "thumb_";
    private static final String DEFAULT_CATEGORY_NAME = "other";

    @Autowired
    private StorageService storageService;

    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private HeicHeifConversionService conversionService;

    @Transactional
    public void upload(UploadForm form) {
        var file = form.getImage();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }

        var contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are allowed");
        }

        var originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "capture.jpg";
        var fileExt = getFileExtension(originalFilename);
        var randomizedFilename = UUID.randomUUID();
        var storedExt = fileExt;
        var storedContentType = contentType;
        byte[] bytesToStore;

        try (var inputStream = file.getInputStream()) {
            var imageBytes = inputStream.readAllBytes();

            var geolocation = this.readGeoLocation(new ByteArrayInputStream(imageBytes));
            if (geolocation != null) {
                log.info("GeoLocation extracted from image: lat={}, lon={}", geolocation.getLatitude(), geolocation.getLongitude());
                form.setLatitude(String.valueOf(geolocation.getLatitude()));
                form.setLongitude(String.valueOf(geolocation.getLongitude()));
            }

            if (conversionService.isHeicOrHeif(contentType, originalFilename)) {
                bytesToStore = conversionService.convertHeicToJpeg(imageBytes);
                storedExt = ".jpg";
                storedContentType = "image/jpeg";
            } else {
                bytesToStore = imageBytes;
            }

            storageService.upload(randomizedFilename + storedExt, new ByteArrayInputStream(bytesToStore), bytesToStore.length, storedContentType);
            thumbnailService.createThumbnail(randomizedFilename + storedExt, new ByteArrayInputStream(bytesToStore), storedContentType);

        } catch (IOException | ImageProcessingException exception) {
            throw new IllegalStateException("Could not upload image", exception);
        }

        var photo = new Photo();
        photo.setOriginalFilename(originalFilename);
        photo.setInternalFilename(randomizedFilename + storedExt);
        photo.setContentType(storedContentType);
        photo.setSizeBytes(bytesToStore.length);
        photo.setDescription(normalizeDescription(form.getDescription()));
        photo.setLatitude(parseCoordinate(form.getLatitude()));
        photo.setLongitude(parseCoordinate(form.getLongitude()));
        photo.setCategory(resolveCategory(form.getCategory()));
        photo.setTags(resolveTags(form.getTags()));
        photoRepository.save(photo);
    }

    private GeoLocation readGeoLocation(InputStream stream) throws ImageProcessingException, IOException {
        Metadata metadata = ImageMetadataReader.readMetadata(stream);
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory != null && gpsDirectory.containsTag(GpsDirectory.TAG_LATITUDE)) {
            return gpsDirectory.getGeoLocation();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<Photo> latest() {
        return photoRepository.findByDeleteDateIsNull(Sort.by(Sort.Direction.DESC, "uploadedAt")).stream().limit(8).toList();
    }

    @Transactional(readOnly = true)
    public Page<Photo> byCategory(String categoryName, int page, int size) {
        var normalized = normalize(categoryName);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Category is required");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findByDeleteDateIsNullAndCategory_NameIgnoreCase(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Photo> byTag(String tagName, int page, int size) {
        var normalized = normalize(tagName);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Tag is required");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findDistinctByDeleteDateIsNullAndTags_NameIgnoreCase(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Photo> archived(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deleteDate"));
        return photoRepository.findByDeleteDateIsNotNull(pageable);
    }

    @Transactional(readOnly = true)
    public Photo get(Long id) {
        return photoRepository.findByIdAndDeleteDateIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + id));
    }

    @Transactional(readOnly = true)
    public Photo getArchived(Long id) {
        return photoRepository.findByIdAndDeleteDateIsNotNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Archived photo not found: " + id));
    }

    @Transactional
    public Photo update(Long id, PhotoUpdateForm form) {
        var photo = get(id);
        if (!StringUtils.hasText(form.getOriginalFilename())) {
            throw new IllegalArgumentException("Original filename is required");
        }

        photo.setOriginalFilename(form.getOriginalFilename().trim());
        photo.setDescription(normalizeDescription(form.getDescription()));
        photo.setLatitude(parseCoordinate(form.getLatitude()));
        photo.setLongitude(parseCoordinate(form.getLongitude()));
        photo.setCategory(resolveCategory(form.getCategory()));
        photo.setTags(resolveTags(form.getTags()));

        return photoRepository.save(photo);
    }

    @Transactional
    public void archive(Long id) {
        var photo = get(id);
        photo.setDeleteDate(Instant.now());
        photoRepository.save(photo);
    }

    @Transactional
    public void restore(Long id) {
        var photo = photoRepository.findByIdAndDeleteDateIsNotNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Archived photo not found: " + id));
        photo.setDeleteDate(null);
        photoRepository.save(photo);
    }

    @Transactional
    public int purgeArchivedOlderThanDays(int retentionDays) {
        var cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
        var candidates = photoRepository.findByDeleteDateBefore(cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }

        for (var photo : candidates) {
            var originalKey = photo.getInternalFilename();
            storageService.delete(THUMBNAIL_KEY_PREFIX + originalKey);
            storageService.delete(originalKey);
        }

        var count = candidates.size();
        photoRepository.deleteAllInBatch(candidates);
        return count;
    }

    @Transactional
    public int archivePhotosWithMissingFiles() {
        var activePhotos = photoRepository.findByDeleteDateIsNull(Sort.unsorted());
        if (activePhotos.isEmpty()) {
            return 0;
        }

        int archivedCount = 0;
        for (var photo : activePhotos) {
            var internalFilename = photo.getInternalFilename();
            var originalExists = storageService.get(internalFilename).isPresent();
            var thumbnailExists = storageService.get(THUMBNAIL_KEY_PREFIX + internalFilename).isPresent();

            // Archive if either the original or thumbnail is missing
            if (!originalExists || !thumbnailExists) {
                photo.setDeleteDate(Instant.now());
                photoRepository.save(photo);
                archivedCount++;
                log.warn("Archived photo {} due to missing storage file(s): original={}, thumbnail={}",
                        photo.getId(), originalExists, thumbnailExists);
            }
        }

        return archivedCount;
    }

    @Transactional
    public int reconcileStorageFiles() {
        var storageKeys = storageService.listKeys();
        if (storageKeys.isEmpty()) {
            return 0;
        }

        var defaultCategory = categoryRepository.findByNameIgnoreCase(DEFAULT_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.save(new Category(DEFAULT_CATEGORY_NAME)));

        int createdCount = 0;
        for (var key : storageKeys) {
            if (!isOriginalStorageKey(key)) {
                continue;
            }

            try {
                var originalObject = storageService.get(key).orElse(null);
                if (originalObject == null || !originalObject.contentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
                    continue;
                }

                var bytes = originalObject.content().readAllBytes();

                if (photoRepository.findByInternalFilename(key).isEmpty()) {
                    var photo = new Photo();
                    photo.setOriginalFilename(key);
                    photo.setInternalFilename(key);
                    photo.setContentType(originalObject.contentType());
                    photo.setSizeBytes(originalObject.contentLength());
                    photo.setCategory(defaultCategory);
                    photoRepository.save(photo);
                    createdCount++;
                }

                var thumbnailKey = THUMBNAIL_KEY_PREFIX + key;
                if (storageService.get(thumbnailKey).isEmpty()) {
                    thumbnailService.createThumbnail(key, new ByteArrayInputStream(bytes), originalObject.contentType());
                }
            } catch (Exception e) {
                log.error("Failed to reconcile storage key {}", key, e);
            }
        }

        return createdCount;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    private Category resolveCategory(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Category is required");
        }

        var trimmed = value.trim();
        if (trimmed.matches("\\d+")) {
            var id = Long.parseLong(trimmed);
            return categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown category id: " + id));
        }

        var normalized = normalize(trimmed);
        return categoryRepository.findByNameIgnoreCase(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + normalized));
    }

    private Set<Tag> resolveTags(List<String> values) {
        Set<Tag> result = new LinkedHashSet<>();
        if (values == null || values.isEmpty()) {
            return result;
        }
        for (String value : values) {
            var normalized = normalize(value);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            var tag = tagRepository.findByNameIgnoreCase(normalized)
                    .orElseGet(() -> tagRepository.save(new Tag(normalized)));
            result.add(tag);
        }

        return result;
    }

    private BigDecimal parseCoordinate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid coordinate format: expected decimal number", exception);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return ".jpg";
        }
        var idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return ".jpg";
        }
        return filename.substring(idx).toLowerCase(Locale.ROOT);
    }

    private boolean isOriginalStorageKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        return !key.startsWith(THUMBNAIL_KEY_PREFIX);
    }

    public Page<Photo> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findByDeleteDateIsNull(pageable);
    }
}
