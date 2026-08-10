package afb.astyann.documentservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A per-approval copy of the generated .docx path, keyed by the VersionService snapshotId
 * created at the same time. Document itself only ever holds the current live file (each
 * regenerate overwrites it in place), so without this table "activate an old version" would
 * have no content to restore — VersionService's Snapshot only tracks metadata (artifactId,
 * versionNumber, active flag), not the artifact itself.
 */
@Entity
@Table(name = "document_version_archives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersionArchive {

    @Id
    @Column(name = "snapshot_id", updatable = false, nullable = false)
    private UUID snapshotId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "file_path")
    private String filePath;

    /**
     * The structured JSON behind {@link #filePath}'s {@code .docx}, captured together with it so a
     * restore can put both back. Restoring only the file would leave the document's live JSON
     * describing the version that was rolled away from.
     */
    @Column(name = "content_json", columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "archived_at", updatable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    void prePersist() {
        archivedAt = LocalDateTime.now();
    }
}
