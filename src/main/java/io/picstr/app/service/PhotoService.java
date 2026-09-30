package io.picstr.app.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import io.picstr.app.form.UploadForm;
import io.picstr.app.form.PhotoSearchForm;
import io.picstr.app.form.PhotoUpdateForm;
import io.picstr.app.model.Category;
import io.picstr.app.model.NameRules;
import io.picstr.app.model.Photo;
import io.picstr.app.model.Tag;
import io.picstr.app.model.ThumbnailKeys;
import io.picstr.app.repository.CategoryRepository;
import io.picstr.app.repository.PhotoRepository;
import io.picstr.app.repository.PhotoSpecifications;
import io.picstr.app.repository.TagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class PhotoService {

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

    /**
     * Stores one image with the shared metadata of the form (category, tags, description, coordinates).
     * Coordinates from the image's EXIF GPS data take precedence over the form's. The form is not modified,
     * so it can be reused for the next image of a bulk upload.
     */
    @Transactional
    public void upload(MultipartFile file, UploadForm form) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }

        var contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are allowed");
        }

        // Resolve category and tags before writing anything to storage, so invalid input leaves no files behind.
        var category = resolveCategory(form.getCategory());
        var tags = resolveTags(form.getTags());
        var latitude = parseCoordinate(form.getLatitude());
        var longitude = parseCoordinate(form.getLongitude());

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
                // Match the column scale (DECIMAL(10,7))
                latitude = BigDecimal.valueOf(geolocation.getLatitude()).setScale(7, RoundingMode.HALF_UP);
                longitude = BigDecimal.valueOf(geolocation.getLongitude()).setScale(7, RoundingMode.HALF_UP);
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
        photo.setLatitude(latitude);
        photo.setLongitude(longitude);
        photo.setCategory(category);
        photo.setTags(tags);
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

    /** Outcome of a bulk action: photos changed, and photos left out (e.g. because of the tag limit). */
    public record BulkResult(int changed, int skipped) {
    }

    @Transactional
    public BulkResult bulkArchive(List<Long> ids) {
        int changed = 0;
        for (var photo : activePhotos(ids)) {
            photo.setDeleteDate(Instant.now());
            changed++;
        }
        return new BulkResult(changed, 0);
    }

    @Transactional
    public BulkResult bulkRestore(List<Long> ids) {
        int changed = 0;
        for (var photo : photoRepository.findAllById(ids)) {
            if (photo.getDeleteDate() != null) {
                photo.setDeleteDate(null);
                changed++;
            }
        }
        return new BulkResult(changed, 0);
    }

    @Transactional
    public BulkResult bulkSetCategory(List<Long> ids, String categoryValue) {
        var category = resolveCategory(categoryValue);
        int changed = 0;
        for (var photo : activePhotos(ids)) {
            if (!category.equals(photo.getCategory())) {
                photo.setCategory(category);
                changed++;
            }
        }
        return new BulkResult(changed, 0);
    }

    /** Adds the tags to every photo; photos that would end up with more than {@link Photo#MAX_TAGS} are skipped. */
    @Transactional
    public BulkResult bulkAddTags(List<Long> ids, List<String> tagNames) {
        var tags = resolveTags(tagNames);
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one tag");
        }
        int changed = 0;
        int skipped = 0;
        for (var photo : activePhotos(ids)) {
            var combined = new LinkedHashSet<>(photo.getTags());
            if (!combined.addAll(tags)) {
                continue;
            }
            if (combined.size() > Photo.MAX_TAGS) {
                skipped++;
                continue;
            }
            photo.setTags(combined);
            changed++;
        }
        return new BulkResult(changed, skipped);
    }

    @Transactional
    public BulkResult bulkRemoveTags(List<Long> ids, List<String> tagNames) {
        var names = tagNames == null ? Set.<String>of() : tagNames.stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one tag");
        }
        int changed = 0;
        for (var photo : activePhotos(ids)) {
            if (photo.getTags().removeIf(tag -> names.contains(tag.getName().toLowerCase(Locale.ROOT)))) {
                changed++;
            }
        }
        return new BulkResult(changed, 0);
    }

    private List<Photo> activePhotos(List<Long> ids) {
        return photoRepository.findAllById(ids).stream()
                .filter(photo -> photo.getDeleteDate() == null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Photo> search(PhotoSearchForm search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findAll(PhotoSpecifications.matching(search), pageable);
    }

    @Transactional(readOnly = true)
    public List<MapMarker> mapMarkers() {
        return photoRepository.findActiveLocations().stream()
                .map(location -> new MapMarker(
                        location.id(),
                        location.latitude().doubleValue(),
                        location.longitude().doubleValue(),
                        location.originalFilename(),
                        "/assets/" + ThumbnailKeys.forOriginal(location.internalFilename())))
                .toList();
    }

    /** One photo on the gallery map, as sent to the browser. */
    public record MapMarker(long id, double latitude, double longitude, String title, String thumbnailUrl) {
    }

    @Transactional(readOnly = true)
    public List<Photo> recentForFeed(int limit) {
        var safeLimit = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findByDeleteDateIsNull(pageable).getContent();
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

    // Deliberately not @Transactional: each repository delete commits on its own, so one failing photo
    // doesn't roll back the ones whose storage files are already gone.
    public int purgeArchivedOlderThanDays(int retentionDays) {
        var cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
        var candidates = photoRepository.findByDeleteDateBefore(cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }

        var purged = 0;
        for (var photo : candidates) {
            try {
                // Storage deletes are idempotent, so a photo whose DB delete fails is retried on the next run.
                var originalKey = photo.getInternalFilename();
                var thumbnailKey = ThumbnailKeys.forOriginal(originalKey);
                var legacyThumbnailKey = ThumbnailKeys.legacyForOriginal(originalKey);
                storageService.delete(thumbnailKey);
                if (!legacyThumbnailKey.equals(thumbnailKey)) {
                    storageService.delete(legacyThumbnailKey);
                }
                storageService.delete(originalKey);
                // Entity delete (not a bulk delete) so JPA also removes the photo_tags rows.
                photoRepository.delete(photo);
                purged++;
            } catch (RuntimeException e) {
                log.error("Failed to purge archived photo {}", photo.getId(), e);
            }
        }
        return purged;
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
            var originalExists = storageService.exists(internalFilename);
            // A thumbnail under the legacy key still counts until ThumbnailKeyMigration has moved it
            var thumbnailKey = ThumbnailKeys.forOriginal(internalFilename);
            var legacyThumbnailKey = ThumbnailKeys.legacyForOriginal(internalFilename);
            var thumbnailExists = storageService.exists(thumbnailKey)
                    || (!legacyThumbnailKey.equals(thumbnailKey) && storageService.exists(legacyThumbnailKey));

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
                var hasRecord = photoRepository.findByInternalFilename(key).isPresent();
                var thumbnailKey = ThumbnailKeys.forOriginal(key);
                var hasThumbnail = storageService.exists(thumbnailKey);
                if (hasRecord && hasThumbnail) {
                    continue;
                }

                var originalObject = storageService.get(key).orElse(null);
                if (originalObject == null || !originalObject.contentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
                    continue;
                }

                var bytes = originalObject.content().readAllBytes();

                if (!hasRecord) {
                    var photo = new Photo();
                    photo.setOriginalFilename(key);
                    photo.setInternalFilename(key);
                    photo.setContentType(originalObject.contentType());
                    photo.setSizeBytes(originalObject.contentLength());
                    photo.setCategory(defaultCategory);
                    photoRepository.save(photo);
                    createdCount++;
                }

                if (!hasThumbnail) {
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
            // Existing tags are reused as they are; only new names must follow the naming rules.
            var tag = tagRepository.findByNameIgnoreCase(normalized)
                    .orElseGet(() -> tagRepository.save(new Tag(NameRules.normalize("Tag", normalized))));
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
        return !ThumbnailKeys.isThumbnail(key);
    }

    public Page<Photo> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return photoRepository.findByDeleteDateIsNull(pageable);
    }
}
