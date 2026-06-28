package afb.astyann.requirementservice.dto.pcsf;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatchFieldRequest {
    @NotBlank
    private String path;
    private String value;
}
