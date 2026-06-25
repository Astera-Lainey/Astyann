package afb.astyann.aiorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InferenceResponseDTO {
    private String model;
    private String content;
}
