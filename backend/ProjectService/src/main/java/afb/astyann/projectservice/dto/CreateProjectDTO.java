package afb.astyann.projectservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectDTO {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
}
