package afb.astyann.requirementservice.dto.rag;

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
public class RagIndexItemDTO {
    private UUID projectId;
    private String sourceType;
    private UUID sourceId;
    private String content;
    private Map<String, String> metadata;
}
