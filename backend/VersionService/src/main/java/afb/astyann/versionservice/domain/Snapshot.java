package afb.astyann.versionservice.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "snapshots")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id", updatable = false, nullable = false)
    private UUID snapId;

    @Column(name = "timeline_id", nullable = false)
    private UUID timelineId;

    @Column(name = "version_name", length = 100)
    private String versionName;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "snapshot_date", updatable = false)
    private LocalDateTime snapDate;

    /** Nullable, self-referential reference to another Snapshot's snapId — the upstream
     * artifact-snapshot this one was derived from (provenance chain across artifact types). */
    @Column(name = "entry_source")
    private UUID entrySource;

    /** Human-readable reason this snapshot was created, e.g. "Diagram approved". */
    @Column(name = "trigger_reason", length = 255)
    private String triggerReason;

    @Column(name = "artifact_path")
    private String artifactPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false)
    private ArtifactType artifactType;

    /** Which concrete artifact instance (a specific diagram/document/code-module/deployment
     * package) this version belongs to — scopes version numbering and the active-snapshot
     * flag per artifact, not per artifactType category. */
    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void prePersist() {
        snapDate = LocalDateTime.now();
    }
}
