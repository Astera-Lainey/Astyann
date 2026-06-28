package afb.astyann.aiorchestrator.dto;

import afb.astyann.aiorchestrator.domain.AITaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AIRequestDTO {
    @NotNull(message = "projectId is required")
    private UUID projectId;
    @NotNull(message = "taskType is required")
    private AITaskType taskType;
    @NotBlank(message = "prompt is required")
    private String prompt;
    private List<String> contextKeys;
}
