package io.picstr.app.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import io.picstr.app.model.Category;
import io.picstr.app.model.Photo;
import io.picstr.app.model.ProcessingStatus;
import io.picstr.app.model.Tag;
import org.springframework.data.domain.Page;

/** JSON representations used by the REST API. */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record CategoryDto(Long id, String name, String color, String description) {
        static CategoryDto of(Category category) {
            return new CategoryDto(category.getId(), category.getName(), category.getColor(), category.getDescription());
        }
    }

    public record TagDto(Long id, String name, String color, String description) {
        static TagDto of(Tag tag) {
            return new TagDto(tag.getId(), tag.getName(), tag.getColor(), tag.getDescription());
        }
    }

    public record PhotoDto(Long id, String originalFilename, String description, String contentType, long sizeBytes,
                           BigDecimal latitude, BigDecimal longitude, CategoryDto category, List<TagDto> tags,
                           Instant uploadedAt, Instant archivedAt, boolean archived, ProcessingStatus status,
                           String fileUrl, String thumbnailUrl) {
        static PhotoDto of(Photo photo) {
            var base = "/api/v1/photos/" + photo.getId();
            return new PhotoDto(photo.getId(), photo.getOriginalFilename(), photo.getDescription(),
                    photo.getContentType(), photo.getSizeBytes(), photo.getLatitude(), photo.getLongitude(),
                    CategoryDto.of(photo.getCategory()),
                    photo.getTags().stream().sorted(Comparator.comparing(Tag::getName)).map(TagDto::of).toList(),
                    photo.getUploadedAt(), photo.getDeleteDate(), photo.getDeleteDate() != null,
                    photo.getProcessingStatus(), base + "/file", base + "/thumbnail");
        }
    }

    public record PageDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        static PageDto<PhotoDto> of(Page<Photo> page) {
            return new PageDto<>(page.getContent().stream().map(PhotoDto::of).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record UploadFailure(String filename, String error) {
    }

    public record UploadResult(List<PhotoDto> created, List<UploadFailure> failed) {
    }

    /** Body of POST /api/v1/photos/bulk; action is archive, restore, category, addTags or removeTags. */
    public record BulkRequest(List<Long> ids, String action, String category, List<String> tags) {
    }
}
