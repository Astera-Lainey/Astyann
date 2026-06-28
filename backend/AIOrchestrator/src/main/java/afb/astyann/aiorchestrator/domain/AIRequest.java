package afb.astyann.aiorchestrator.domain;

import afb.astyann.aiorchestrator.provider.ProviderConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AIRequest {
    private UUID requestId;
    private UUID projectId;
    private AITaskType taskType;
    private String prompt;
    private String context;
    private ProviderConfig config;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
