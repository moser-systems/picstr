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

    public boolean isEmpty() {
        return !StringUtils.hasText(q) && !StringUtils.hasText(category) && !StringUtils.hasText(tag) && !located;
    }
}
