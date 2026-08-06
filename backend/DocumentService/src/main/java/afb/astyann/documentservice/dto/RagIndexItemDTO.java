package afb.astyann.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RagIndexItemDTO {
    private UUID projectId;
    private String sourceType;
    private UUID sourceId;
    /** Approved version this text was extracted from; null until the snapshot is confirmed. */
    private UUID snapshotId;
    private String content;
    private Map<String, String> metadata;
}
