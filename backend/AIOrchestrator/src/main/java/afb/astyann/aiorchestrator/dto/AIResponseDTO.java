package afb.astyann.aiorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponseDTO {
    private UUID responseId;
    private String content;
    private String providerName;
    private int tokensUsed;
}
