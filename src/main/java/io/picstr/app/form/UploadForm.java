package io.picstr.app.form;

import io.picstr.app.model.Photo;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UploadForm {

    /** Maximum number of images per upload. */
    public static final int MAX_IMAGES = 20;

    /** Files from the camera input and the multi-file picker; empty inputs arrive as empty parts. */
    private List<MultipartFile> images = new ArrayList<>();

    private String latitude;

    private String longitude;

    @Size(max = 1000)
    private String description;

    @Size(max = Photo.MAX_TAGS, message = "{msg.tags.max}")
    private List<String> tags = new ArrayList<>();

    @NotBlank
    private String category;

    public List<MultipartFile> nonEmptyImages() {
        return images == null ? List.of() : images.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }
}
