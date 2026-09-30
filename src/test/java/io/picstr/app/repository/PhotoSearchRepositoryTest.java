package io.picstr.app.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private Category places;
    private Tag beach;
    private Tag sunset;

    @BeforeEach
    void setUp() {
        travel = entityManager.persist(new Category("travel"));
        places = entityManager.persist(new Category("places"));
        var work = entityManager.persist(new Category("work"));
        beach = entityManager.persist(new Tag("beach"));
        sunset = entityManager.persist(new Tag("sunset"));

        photo("IMG_0001.jpg", "Sunset in Lisbon", travel, Set.of(beach, sunset), true, false);
        photo("office.png", null, work, Set.of(), false, false);
        photo("100%_done.jpg", "Launch party!", work, Set.of(), false, false);
        photo("archived-beach.jpg", "Old beach day", travel, Set.of(beach), true, true);
        located("zurich.jpg", 47.3769, 8.5417);
        located("fiji.jpg", -18.1416, 178.4419);
        located("samoa.jpg", -13.7590, -172.1046);
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
        located.setQ("img");
        assertThat(filenames(located)).containsExactly("IMG_0001.jpg");
    }

    @Test
    void archivedPhotosAreNeverReturned() {
        assertThat(filenames(search("archived"))).isEmpty();
    }

    @Test
    void areaMatchesPhotosInsideTheBoundingBox() {
        // Western Europe: Lisbon and Zurich, not the archived Lisbon photo
        assertThat(filenames(area(50, 35, 10, -10))).containsExactlyInAnyOrder("IMG_0001.jpg", "zurich.jpg");
        assertThat(filenames(area(50, 45, 10, 5))).containsExactly("zurich.jpg");
    }

    @Test
    void areaAcrossTheAntimeridianWrapsAround() {
        // west 170°E to east 170°W covers Fiji and Samoa, nothing in Europe
        assertThat(filenames(area(0, -30, -170, 170))).containsExactlyInAnyOrder("fiji.jpg", "samoa.jpg");
    }

    @Test
    void areaCombinesWithText() {
        var search = area(50, 35, 10, -10);
        search.setQ("zurich");
        assertThat(filenames(search)).containsExactly("zurich.jpg");
    }

    @Test
    void incompleteAreaIsIgnoredAndInvalidAreaRejected() {
        var incomplete = new PhotoSearchForm();
        incomplete.setNorth(50.0);
        assertThat(incomplete.isEmpty()).isTrue();

        assertThatThrownBy(() -> filenames(area(10, 20, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid map area");
        assertThatThrownBy(() -> filenames(area(95, 0, 0, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filenames(area(10, 0, 181, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    private static PhotoSearchForm area(double north, double south, double east, double west) {
        var search = new PhotoSearchForm();
        search.setNorth(north);
        search.setSouth(south);
        search.setEast(east);
        search.setWest(west);
        return search;
    }

    private void located(String filename, double latitude, double longitude) {
        photo(filename, null, places, Set.of(), false, false);
        var photo = photoRepository.findAll().stream().filter(p -> p.getOriginalFilename().equals(filename)).findFirst().orElseThrow();
        photo.setLatitude(BigDecimal.valueOf(latitude));
        photo.setLongitude(BigDecimal.valueOf(longitude));
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
