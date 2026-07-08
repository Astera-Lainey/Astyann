package afb.astyann.diagramgeneratorservice.dto;

import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class GenerateDiagramsRequest {
    private List<DiagramType> diagramTypes;

    @Builder.Default
    private String renderFormat = "SVG";
}
