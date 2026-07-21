package afb.astyann.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApproveDocumentResponse {
    private String status;
    private ValidationReportDTO validationReport;
    private boolean allDocumentsApproved;
}
