package io.picstr.app.form;

import io.picstr.app.model.NameRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryForm {

    @NotBlank()
    @Size(min = NameRules.MIN_LENGTH, max = NameRules.MAX_LENGTH)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank(message = "{msg.color.required}")
    @Pattern(regexp = "blue|azure|indigo|purple|pink|red|orange|yellow|lime|green|teal|cyan", message = "{msg.color.invalid}")
    private String color = "blue";
}
