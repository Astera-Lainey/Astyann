package afb.astyann.diagramgeneratorservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApproveDiagramsResponse {
    private List<UUID> snapshotIds;
    private int updatedCount;
    private boolean allDiagramsApproved;
}
