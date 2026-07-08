package afb.astyann.diagramgeneratorservice.dto;

import afb.astyann.diagramgeneratorservice.domain.DiagramStatus;
import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DiagramListItemDTO {
    private UUID diagramId;
    private DiagramType type;
    private DiagramStatus status;
    private LocalDateTime updatedAt;
}
