package afb.astyann.requirementservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeRequestBody {
    @NotBlank
    private String instructions;
}
