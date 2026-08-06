package afb.astyann.ragservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IndexRequestDTO {
    private UUID projectId;
    private String sourceType;
    private UUID sourceId;
    /**
     * Version this content was taken from, when the caller has one. Stamped into chunk metadata so
     * a retrieved passage can be traced back to the approved version that produced it. Optional —
     * callers with no versioning (or an approval whose snapshot has not landed yet) send null.
     */
    private UUID snapshotId;
    private String content;
    private Map<String, String> metadata;
}
