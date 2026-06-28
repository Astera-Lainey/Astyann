package afb.astyann.projectservice.dto;

import afb.astyann.projectservice.domain.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {
    private UUID projectId;
    private UUID userId;
    private String title;
    private String description;
    private ProjectStatus status;
    private LocalDateTime creationDate;
    private LocalDateTime updatedDate;
}
