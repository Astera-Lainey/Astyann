package afb.astyann.diagramgeneratorservice.dto;

import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DiagramFailureDTO {
    private DiagramType type;
    private String reason;
}