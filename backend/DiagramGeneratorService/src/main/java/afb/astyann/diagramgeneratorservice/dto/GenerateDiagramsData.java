package afb.astyann.diagramgeneratorservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class GenerateDiagramsData {
    private List<DiagramSummaryDTO> diagrams;
}
