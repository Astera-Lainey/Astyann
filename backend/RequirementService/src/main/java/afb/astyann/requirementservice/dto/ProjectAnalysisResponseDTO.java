package afb.astyann.requirementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAnalysisResponseDTO {
    private UUID projectId;
    private boolean sufficient;
    private String extractedContext;
    private List<String> guidedQuestions;
    private String documentText;
}
