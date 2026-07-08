package afb.astyann.versionservice.dto;

import afb.astyann.versionservice.domain.ArtifactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class SnapshotDTO {
    private UUID snapId;
    private UUID timelineId;
    private String versionName;
    private Integer versionNumber;
    private LocalDateTime snapDate;
    private UUID entrySource;
    private String triggerReason;
    private String artifactPath;
    private ArtifactType artifactType;
    private boolean active;

    private UUID diagramId;
    private String diagramType;
    private UUID documentId;
    private String documentType;
    private UUID codeId;
    private String codeLayer;
    private UUID packageId;
}
