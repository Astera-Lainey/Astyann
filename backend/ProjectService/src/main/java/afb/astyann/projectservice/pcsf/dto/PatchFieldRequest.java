package afb.astyann.projectservice.pcsf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatchFieldRequest {
    @NotBlank
    private String path;
    private String value;
}
