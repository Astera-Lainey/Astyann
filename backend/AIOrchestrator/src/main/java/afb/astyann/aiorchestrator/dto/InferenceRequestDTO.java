package afb.astyann.aiorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InferenceRequestDTO {
    @NotBlank
    private String model;
    private String systemPrompt;
    @NotBlank
    private String userPrompt;
}
