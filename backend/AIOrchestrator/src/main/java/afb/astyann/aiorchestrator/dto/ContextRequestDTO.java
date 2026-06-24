package afb.astyann.aiorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ContextRequestDTO {
    @NotNull(message = "projectId is required")
    private UUID projectId;
    @NotBlank(message = "query is required")
    private String query;
    private int topK = 5;
}
