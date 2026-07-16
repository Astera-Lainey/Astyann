package afb.astyann.documentservice.dto;

import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DocumentSummaryDTO {
    private UUID documentId;
    private DocumentType type;
    private DocumentStatus status;
    private Integer pageCount;
    private String lastError;
    private UUID previousVersionId;
}
