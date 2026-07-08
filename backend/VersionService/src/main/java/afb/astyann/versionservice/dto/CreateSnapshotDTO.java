package afb.astyann.versionservice.dto;

import afb.astyann.versionservice.domain.ArtifactType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CreateSnapshotDTO {

    @NotNull
    private UUID projectId;

    @NotNull
    private ArtifactType artifactType;

    private String versionName;

    /** Nullable — self-referential provenance link to another Snapshot's snapId. */
    private UUID entrySource;

    /** Nullable — human-readable reason this snapshot was created. */
    private String triggerReason;

    private String artifactPath;

    // Exactly one of the following groups should be populated, matching artifactType.
    private UUID diagramId;
    private String diagramType;

    private UUID documentId;
    private String documentType;

    private UUID codeId;
    private String codeLayer;

    private UUID packageId;
}
