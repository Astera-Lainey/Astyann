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

    /**
     * The structured JSON this document's {@code .docx} was merged from.
     *
     * <p>Documents are schema-driven: the model returns JSON, which is merged into a Word template.
     * Only the merged {@code .docx} used to be kept, so the machine-readable form was destroyed and
     * then partially reconstructed downstream by extracting prose back out of the Word file. Keeping
     * it means a consumer can read the functional requirements, use cases and module responsibilities
     * as data rather than as retrieved text.
     *
     * <p>Null for documents generated before this was stored — that is a distinguishable state, not
     * an error, and the read API reports it as such rather than returning empty content.
     */
    @Column(name = "content_json", columnDefinition = "LONGTEXT")
    private String contentJson;

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
