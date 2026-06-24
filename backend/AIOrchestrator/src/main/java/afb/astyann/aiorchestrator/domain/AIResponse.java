package afb.astyann.aiorchestrator.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AIResponse {
    private UUID responseId;
    private UUID requestId;
    private String content;
    private String providerName;
    private int tokensUsed;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
