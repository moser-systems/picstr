package io.picstr.app.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import io.picstr.app.form.PhotoSearchForm;
import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Runs the search specifications against H2 with the real Flyway schema. */
@DataJpaTest
class PhotoSearchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PhotoRepository photoRepository;

    private Category travel;
    private Tag beach;
    private Tag sunset;

    @BeforeEach
    void setUp() {
        travel = entityManager.persist(new Category("travel"));
        var work = entityManager.persist(new Category("work"));
        beach = entityManager.persist(new Tag("beach"));
        sunset = entityManager.persist(new Tag("sunset"));

        photo("IMG_0001.jpg", "Sunset in Lisbon", travel, Set.of(beach, sunset), true, false);
        photo("office.png", null, work, Set.of(), false, false);
        photo("100%_done.jpg", "Launch party!", work, Set.of(), false, false);
        photo("archived-beach.jpg", "Old beach day", travel, Set.of(beach), true, true);
        entityManager.flush();
    }

    @Test
    void textMatchesFilenameDescriptionCategoryAndTagsIgnoringCase() {
        assertThat(filenames(search("img_0001"))).containsExactly("IMG_0001.jpg");
        assertThat(filenames(search("LISBON"))).containsExactly("IMG_0001.jpg");
        assertThat(filenames(search("work"))).containsExactlyInAnyOrder("office.png", "100%_done.jpg");
        assertThat(filenames(search("beach"))).containsExactly("IMG_0001.jpg");
    }

    @Test
    void photoWithSeveralMatchingTagsIsReturnedOnce() {
        var result = photoRepository.findAll(PhotoSpecifications.matching(form("s")), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Photo::getOriginalFilename).doesNotHaveDuplicates();
        assertThat(result.getTotalElements()).isEqualTo(result.getContent().size());
    }

    @Test
    void likeWildcardsInTheTextAreMatchedLiterally() {
        assertThat(filenames(search("100%"))).containsExactly("100%_done.jpg");
        assertThat(filenames(search("_"))).containsExactlyInAnyOrder("IMG_0001.jpg", "100%_done.jpg");
        assertThat(filenames(search("party!"))).containsExactly("100%_done.jpg");
    }

    @Test
    void filtersCombineWithText() {
        var byCategory = new PhotoSearchForm();
        byCategory.setCategory("Travel");
        assertThat(filenames(byCategory)).containsExactly("IMG_0001.jpg");

        var byTag = new PhotoSearchForm();
        byTag.setTag("sunset");
        assertThat(filenames(byTag)).containsExactly("IMG_0001.jpg");

        var located = new PhotoSearchForm();
        located.setLocated(true);
        located.setQ("jpg");
        assertThat(filenames(located)).containsExactly("IMG_0001.jpg");
    }

    @Test
    void archivedPhotosAreNeverReturned() {
        assertThat(filenames(search("archived"))).isEmpty();
    }

    private void photo(String filename, String description, Category category, Set<Tag> tags,
                       boolean located, boolean archived) {
        var photo = new Photo();
        photo.setOriginalFilename(filename);
        photo.setInternalFilename(filename.replace("%", "") + "-" + System.nanoTime());
        photo.setContentType("image/jpeg");
        photo.setSizeBytes(1);
        photo.setDescription(description);
        photo.setCategory(category);
        photo.setTags(new java.util.LinkedHashSet<>(tags));
        if (located) {
            photo.setLatitude(new BigDecimal("38.7223"));
            photo.setLongitude(new BigDecimal("-9.1393"));
        }
        if (archived) {
            photo.setDeleteDate(Instant.now());
        }
        entityManager.persist(photo);
    }

    private static PhotoSearchForm search(String text) {
        return form(text);
    }

    private static PhotoSearchForm form(String text) {
        var form = new PhotoSearchForm();
        form.setQ(text);
        return form;
    }

    private java.util.List<String> filenames(PhotoSearchForm form) {
        return photoRepository.findAll(PhotoSpecifications.matching(form), Sort.by("originalFilename")).stream()
                .map(Photo::getOriginalFilename)
                .toList();
    }
}
