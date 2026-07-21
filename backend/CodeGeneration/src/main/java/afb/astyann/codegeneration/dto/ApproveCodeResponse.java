package afb.astyann.codegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApproveCodeResponse {
    private List<UUID> snapshotIds;
    private int updatedCount;
    private boolean allLayersApproved;
}
