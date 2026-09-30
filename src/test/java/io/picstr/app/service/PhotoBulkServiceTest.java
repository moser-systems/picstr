package io.picstr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Bulk actions against H2 and the real schema, so tag links and dirty checking are exercised. */
@DataJpaTest
@Import(PhotoService.class)
class PhotoBulkServiceTest {

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private ThumbnailService thumbnailService;

    @MockitoBean
    private HeicHeifConversionService conversionService;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private TestEntityManager entityManager;

    private Category travel;
    private Category work;
    private Tag beach;
    private Photo first;
    private Photo second;
    private Photo archived;

    @BeforeEach
    void setUp() {
        travel = entityManager.persist(new Category("travel"));
        work = entityManager.persist(new Category("work"));
        beach = entityManager.persist(new Tag("beach"));
        first = photo("a.jpg", travel, Set.of(beach), false);
        second = photo("b.jpg", travel, Set.of(), false);
        archived = photo("c.jpg", travel, Set.of(), true);
        entityManager.flush();
    }

    @Test
    void archiveSkipsAlreadyArchivedPhotos() {
        var result = photoService.bulkArchive(ids(first, second, archived));

        assertThat(result.changed()).isEqualTo(2);
        assertThat(reload(first).getDeleteDate()).isNotNull();
        assertThat(reload(second).getDeleteDate()).isNotNull();
    }

    @Test
    void restoreOnlyTouchesArchivedPhotos() {
        var result = photoService.bulkRestore(ids(first, archived));

        assertThat(result.changed()).isEqualTo(1);
        assertThat(reload(archived).getDeleteDate()).isNull();
    }

    @Test
    void setCategoryChangesActivePhotosOnly() {
        var result = photoService.bulkSetCategory(ids(first, second, archived), String.valueOf(work.getId()));

        assertThat(result.changed()).isEqualTo(2);
        assertThat(reload(first).getCategory().getName()).isEqualTo("work");
        assertThat(reload(archived).getCategory().getName()).isEqualTo("travel");
    }

    @Test
    void addTagsCreatesNewTagsAndPersistsLinks() {
        var result = photoService.bulkAddTags(ids(first, second), List.of("beach", "Sunset"));

        assertThat(result).isEqualTo(new PhotoService.BulkResult(2, 0));
        assertThat(tagNames(reload(first))).containsExactlyInAnyOrder("beach", "sunset");
        assertThat(tagNames(reload(second))).containsExactlyInAnyOrder("beach", "sunset");
    }

    @Test
    void addTagsSkipsPhotosThatWouldExceedTheLimit() {
        photoService.bulkAddTags(ids(first), List.of("t1", "t2", "t3", "t4"));

        var result = photoService.bulkAddTags(ids(first, second), List.of("extra"));

        assertThat(result).isEqualTo(new PhotoService.BulkResult(1, 1));
        assertThat(tagNames(reload(first))).hasSize(Photo.MAX_TAGS).doesNotContain("extra");
        assertThat(tagNames(reload(second))).containsExactly("extra");
    }

    @Test
    void addTagsRejectsTooShortNewTagNames() {
        assertThatThrownBy(() -> photoService.bulkAddTags(ids(first), List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeTagsIgnoresCaseAndCountsOnlyChangedPhotos() {
        var result = photoService.bulkRemoveTags(ids(first, second), List.of("BEACH"));

        assertThat(result.changed()).isEqualTo(1);
        assertThat(reload(first).getTags()).isEmpty();
    }

    @Test
    void tagActionsNeedAtLeastOneTag() {
        assertThatThrownBy(() -> photoService.bulkAddTags(ids(first), List.of(" ")))
                .hasMessage("Choose at least one tag");
        assertThatThrownBy(() -> photoService.bulkRemoveTags(ids(first), null))
                .hasMessage("Choose at least one tag");
    }

    private Photo photo(String filename, Category category, Set<Tag> tags, boolean isArchived) {
        var photo = new Photo();
        photo.setOriginalFilename(filename);
        photo.setInternalFilename(filename);
        photo.setContentType("image/jpeg");
        photo.setSizeBytes(1);
        photo.setCategory(category);
        photo.setTags(new LinkedHashSet<>(tags));
        if (isArchived) {
            photo.setDeleteDate(Instant.now());
        }
        return entityManager.persist(photo);
    }

    private Photo reload(Photo photo) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Photo.class, photo.getId());
    }

    private static List<Long> ids(Photo... photos) {
        return java.util.Arrays.stream(photos).map(Photo::getId).toList();
    }

    private static List<String> tagNames(Photo photo) {
        return photo.getTags().stream().map(Tag::getName).toList();
    }
}
