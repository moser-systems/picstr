package io.picstr.app.model;

import java.math.BigDecimal;

/** The fields of a geotagged photo that the gallery map needs, loaded without tags or category. */
public record PhotoLocation(Long id, BigDecimal latitude, BigDecimal longitude,
                            String internalFilename, String originalFilename) {
}
