package afb.astyann.ragservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalResponseDTO {
    private String context;
    private List<String> chunks;
    private List<String> sources;
    private List<Double> scores;
}
