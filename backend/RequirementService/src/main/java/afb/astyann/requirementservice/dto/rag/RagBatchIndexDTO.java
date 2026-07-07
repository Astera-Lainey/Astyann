package afb.astyann.requirementservice.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagBatchIndexDTO {
    private UUID projectId;
    private String sourceType;
    private List<RagIndexItemDTO> items;
}
