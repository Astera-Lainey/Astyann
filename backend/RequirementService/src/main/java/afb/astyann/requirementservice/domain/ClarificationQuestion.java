package afb.astyann.requirementservice.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clarification_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClarificationQuestion {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @Column(name = "inventory_ref")
    private String inventoryRef;

    @Column(name = "target_path")
    private String targetPath;

    @Column(name = "priority")
    private Integer priority;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private QuestionType type;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(columnDefinition = "TEXT")
    private String placeholder;

    @Column(name = "answered", nullable = false)
    @Builder.Default
    private boolean answered = false;

    @Column(columnDefinition = "TEXT")
    private String answer;
}
