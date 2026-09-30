package io.picstr.app.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UploadForm {

    @NotNull
    private MultipartFile image;

    private String latitude;

    private String longitude;

    @Size(max = 1000)
    private String description;

    @Size(max = 5, message = "{msg.tags.max}")
    private List<String> tags = new ArrayList<>();

    @NotBlank
    private String category;
}
