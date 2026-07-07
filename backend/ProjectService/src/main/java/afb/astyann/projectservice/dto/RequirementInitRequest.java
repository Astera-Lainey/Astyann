package afb.astyann.projectservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @AllArgsConstructor @NoArgsConstructor
public class RequirementInitRequest {
    private UUID   projectId;
    private String projectTitle;
    private String projectDescription;
    private String projectContext;
    private String documentText;
}
