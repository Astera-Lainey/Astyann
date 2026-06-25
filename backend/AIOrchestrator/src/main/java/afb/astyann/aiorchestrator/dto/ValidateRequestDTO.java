package afb.astyann.aiorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ValidateRequestDTO {
    @NotNull(message = "projectId is required")
    private UUID projectId;
    @NotBlank(message = "content is required")
    private String content;
    private String rules;
}
