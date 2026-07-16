package afb.astyann.documentservice.dto;

import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DocumentListItemDTO {
    private UUID documentId;
    private DocumentType type;
    private DocumentStatus status;
    private Integer version;
    private LocalDateTime generatedAt;
    private String lastError;
}
