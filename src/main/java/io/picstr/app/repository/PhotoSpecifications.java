package io.picstr.app.repository;

import java.math.BigDecimal;
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
        if (search.hasArea()) {
            specs.add(inArea(search.getNorth(), search.getSouth(), search.getEast(), search.getWest()));
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

    private static Specification<Photo> inArea(double north, double south, double east, double west) {
        if (south < -90 || north > 90 || south > north
                || west < -180 || west > 180 || east < -180 || east > 180) {
            throw new IllegalArgumentException("Invalid map area: latitudes must be -90..90 with south <= north, "
                    + "longitudes -180..180");
        }
        var minLat = BigDecimal.valueOf(south);
        var maxLat = BigDecimal.valueOf(north);
        var minLon = BigDecimal.valueOf(west);
        var maxLon = BigDecimal.valueOf(east);
        return (root, query, cb) -> {
            var latitude = root.<BigDecimal>get("latitude");
            var longitude = root.<BigDecimal>get("longitude");
            var inLatitude = cb.between(latitude, minLat, maxLat);
            // west > east: the area crosses the antimeridian, so it is two longitude ranges
            var inLongitude = west <= east
                    ? cb.between(longitude, minLon, maxLon)
                    : cb.or(cb.greaterThanOrEqualTo(longitude, minLon), cb.lessThanOrEqualTo(longitude, maxLon));
            return cb.and(inLatitude, inLongitude);
        };
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
