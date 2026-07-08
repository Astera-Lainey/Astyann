package afb.astyann.diagramgeneratorservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "uml_diagrams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UMLDiagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "diagram_id", updatable = false, nullable = false)
    private UUID diagramId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagramType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DiagramStatus status = DiagramStatus.PENDING_APPROVAL;

    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Column(name = "render_format")
    private String renderFormat;

    @Column(name = "generated_image_path")
    private String generatedImagePath;

    @Column(columnDefinition = "TEXT")
    private String feedback;

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
