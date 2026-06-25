package afb.astyann.projectservice.pcsf.dto;

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
