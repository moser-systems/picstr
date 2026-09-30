package io.picstr.app.repository;

import java.util.ArrayList;
import java.util.Locale;

import io.picstr.app.form.PhotoSearchForm;
import io.picstr.app.model.Photo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Query building blocks for photo search. Only active (not archived) photos are ever matched. */
public final class PhotoSpecifications {

    // Not a backslash: MariaDB/MySQL treat backslashes in string literals as escapes themselves.
    private static final char ESCAPE = '!';

    private PhotoSpecifications() {
    }

    public static Specification<Photo> matching(PhotoSearchForm search) {
        var specs = new ArrayList<Specification<Photo>>();
        specs.add((root, query, cb) -> cb.isNull(root.get("deleteDate")));
        if (StringUtils.hasText(search.getQ())) {
            specs.add(containsText(search.getQ()));
        }
        if (StringUtils.hasText(search.getCategory())) {
            specs.add((root, query, cb) ->
                    cb.equal(cb.lower(root.get("category").get("name")), normalize(search.getCategory())));
        }
        if (StringUtils.hasText(search.getTag())) {
            specs.add((root, query, cb) -> hasTag(root, query, cb, normalize(search.getTag()), false));
        }
        if (search.isLocated()) {
            specs.add((root, query, cb) ->
                    cb.and(cb.isNotNull(root.get("latitude")), cb.isNotNull(root.get("longitude"))));
        }
        return Specification.allOf(specs);
    }

    private static Specification<Photo> containsText(String text) {
        var pattern = "%" + escapeLike(normalize(text)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("originalFilename")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("category").get("name")), pattern, ESCAPE),
                hasTag(root, query, cb, pattern, true));
    }

    /** Subquery instead of a join, so a photo with several matching tags is returned once. */
    private static Predicate hasTag(Root<Photo> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                    String value, boolean like) {
        var subquery = query.subquery(Long.class);
        var photo = subquery.from(Photo.class);
        var tag = photo.join("tags");
        var tagName = cb.lower(tag.get("name"));
        subquery.select(photo.get("id")).where(
                cb.equal(photo, root),
                like ? cb.like(tagName, value, ESCAPE) : cb.equal(tagName, value));
        return cb.exists(subquery);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
