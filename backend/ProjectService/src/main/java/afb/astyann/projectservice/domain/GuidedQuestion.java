package afb.astyann.projectservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guided_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuidedQuestion {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "gq_id", updatable = false, nullable = false)
    private UUID gqId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
