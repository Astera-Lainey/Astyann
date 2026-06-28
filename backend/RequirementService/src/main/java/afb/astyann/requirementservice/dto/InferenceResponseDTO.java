package afb.astyann.requirementservice.dto;

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
