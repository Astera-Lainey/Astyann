package afb.astyann.codegeneration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "code_id", updatable = false, nullable = false)
    private UUID codeId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeLayer layer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CodeStatus status = CodeStatus.GENERATING;

    @Column(name = "code_path")
    private String codePath;

    @Column(name = "download_url")
    private String downloadUrl;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /** Set once VersionService confirms a snapshot for this layer's current GENERATED state. */
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    // ── AI logic-injection outcome (BACKEND only; null until a generate/inject pass runs) ──

    /** Total modules the projection asked the AI to implement. */
    @Column(name = "modules_total")
    private Integer modulesTotal;

    /** Modules the AI actually patched (at least one file written). */
    @Column(name = "modules_patched")
    private Integer modulesPatched;

    /**
     * Count of methods still throwing {@code UnsupportedOperationException} after the injection
     * pass. {@code 0} means logic-complete; {@code > 0} blocks approval (see
     * {@code codegen.approve.require-complete}). Stubs compile, so this is the only signal that
     * tells a still-stubbed backend apart from a fully-implemented one.
     */
    @Column(name = "stub_methods_remaining")
    private Integer stubMethodsRemaining;

    @Column(name = "gen_date", updatable = false)
    private LocalDateTime genDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        genDate = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
