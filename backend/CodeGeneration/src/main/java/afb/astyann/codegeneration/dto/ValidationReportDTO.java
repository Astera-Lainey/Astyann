package afb.astyann.codegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ValidationReportDTO {
    private UUID projectId;
    /** PASSED | FAILED */
    private String validationStatus;
    private int attemptsUsed;
    private List<ValidationCheckDTO> checks;
    private List<String> remainingIssues;
}
