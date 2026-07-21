package afb.astyann.diagramgeneratorservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A per-approval copy of the fields needed to actually restore a diagram, keyed by the
 * VersionService snapshotId created at the same time. UMLDiagram itself only ever holds the
 * current live content (each regenerate overwrites it in place), so without this table
 * "activate an old version" would have no content to restore — VersionService's Snapshot
 * only tracks metadata (artifactId, versionNumber, active flag), not the artifact itself.
 */
@Entity
@Table(name = "diagram_version_archives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramVersionArchive {

    @Id
    @Column(name = "snapshot_id", updatable = false, nullable = false)
    private UUID snapshotId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "diagram_id", nullable = false)
    private UUID diagramId;

    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "render_format")
    private String renderFormat;

    @Column(name = "archived_at", updatable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    void prePersist() {
        archivedAt = LocalDateTime.now();
    }
}
