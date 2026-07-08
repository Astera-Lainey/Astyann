package afb.astyann.versionservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "timelines", uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timeline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "timeline_id", updatable = false, nullable = false)
    private UUID timelineId;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    void prePersist() {
        creationDate = LocalDateTime.now();
    }
}
