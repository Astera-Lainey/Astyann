package afb.astyann.aiorchestrator.provider;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderConfig {
    @Builder.Default
    private int maxTokens = 4096;
    @Builder.Default
    private double temperature = 0.7;
    @Builder.Default
    private double topP = 1.0;
    private String systemPrompt;
}
