package io.picstr.app.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhotoUpdateForm {

    @NotBlank()
    @Size(max = 255)
    private String originalFilename;

    private String latitude;

    private String longitude;

    @Size(max = 1000)
    private String description;

    @NotBlank()
    private String category;

    private List<String> tags = new ArrayList<>();
}
