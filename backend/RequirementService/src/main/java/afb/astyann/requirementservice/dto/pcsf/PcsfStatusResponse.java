package afb.astyann.requirementservice.dto.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PcsfStatusResponse {
    private String pcsfStatus;
    private double completenessScore;
    private int pendingQuestionsCount;
}
