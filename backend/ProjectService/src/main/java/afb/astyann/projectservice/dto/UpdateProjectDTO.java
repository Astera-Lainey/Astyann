package afb.astyann.projectservice.dto;

import afb.astyann.projectservice.domain.ProjectStatus;
import lombok.Data;

@Data
public class UpdateProjectDTO {
    private String title;
    private String description;
    private ProjectStatus status;
}
