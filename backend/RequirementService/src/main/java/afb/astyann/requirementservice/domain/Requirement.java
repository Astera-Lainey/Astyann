package afb.astyann.requirementservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "requirements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "requirement_id", updatable = false, nullable = false)
    private UUID requirementId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_title")
    private String projectTitle;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "project_context", columnDefinition = "MEDIUMTEXT")
    private String projectContext;

    @Column(name = "pcsf_json", columnDefinition = "LONGTEXT")
    private String pcsfJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "pcsf_status", nullable = false)
    @Builder.Default
    private PcsfStatus pcsfStatus = PcsfStatus.DRAFT;

    @Column(name = "pending_questions_count", nullable = false)
    @Builder.Default
    private Integer pendingQuestionsCount = 0;

    @Column(name = "change_instructions", columnDefinition = "TEXT")
    private String changeInstructions;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
