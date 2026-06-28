package afb.astyann.aiorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AnalyzeRequestDTO {
    @NotNull(message = "projectId is required")
    private UUID projectId;
    @NotBlank(message = "rawInput is required")
    private String rawInput;
}
