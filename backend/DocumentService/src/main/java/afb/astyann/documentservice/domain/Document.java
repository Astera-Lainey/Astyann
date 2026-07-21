package afb.astyann.documentservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id", updatable = false, nullable = false)
    private UUID documentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.GENERATING;

    @Column(name = "file_path")
    private String path;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "change_instructions", columnDefinition = "TEXT")
    private String changeInstructions;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Set once VersionService confirms a snapshot for this document's current APPROVED state.
     * Null while APPROVED means the snapshot call failed (or hasn't run yet) — the approval
     * itself is not rolled back for that, so this is what the retry job polls on.
     */
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "generated_date", updatable = false)
    private LocalDateTime generatedDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        generatedDate = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
