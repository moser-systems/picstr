package io.picstr.app.form;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/** Search criteria from the query string of /photos/search; every field is optional. */
@Getter
@Setter
public class PhotoSearchForm {

    /** Free text matched against filename, description, category and tag names. */
    private String q;

    /** Category name. */
    private String category;

    /** Tag name. */
    private String tag;

    /** Only photos with coordinates. */
    private boolean located;

    /**
     * Map area (bounding box) in degrees; used only when all four are set. If west is greater than east,
     * the area crosses the antimeridian (±180°).
     */
    private Double north;
    private Double south;
    private Double east;
    private Double west;

    public boolean hasArea() {
        return north != null && south != null && east != null && west != null;
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(q) && !StringUtils.hasText(category) && !StringUtils.hasText(tag) && !located
                && !hasArea();
    }
}
