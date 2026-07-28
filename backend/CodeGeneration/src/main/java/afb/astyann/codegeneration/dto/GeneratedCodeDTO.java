package afb.astyann.codegeneration.dto;

import afb.astyann.codegeneration.domain.CodeLayer;
import afb.astyann.codegeneration.domain.CodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class GeneratedCodeDTO {
    private UUID codeId;
    private CodeLayer layer;
    private CodeStatus status;
    private String downloadUrl;
    private String lastError;
    private LocalDateTime genDate;

    /** AI logic-injection outcome (BACKEND only; null for other layers / before injection). */
    private Integer modulesTotal;
    private Integer modulesPatched;
    private Integer stubMethodsRemaining;
}
