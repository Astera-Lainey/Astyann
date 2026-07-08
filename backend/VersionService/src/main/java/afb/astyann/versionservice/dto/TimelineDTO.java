package afb.astyann.versionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class TimelineDTO {
    private UUID timelineId;
    private UUID projectId;
    private LocalDateTime creationDate;
    private List<SnapshotDTO> snapshots;
}
