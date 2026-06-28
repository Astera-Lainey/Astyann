package afb.astyann.aiorchestrator.service;

import afb.astyann.aiorchestrator.domain.AIRequest;
import afb.astyann.aiorchestrator.domain.AIResponse;
import afb.astyann.aiorchestrator.domain.AITaskType;
import afb.astyann.aiorchestrator.provider.AIProvider;
import afb.astyann.aiorchestrator.provider.OllamaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIProviderRouter {

    private final OllamaProvider ollamaProvider;

    public AIProvider selectProvider(AITaskType taskType) {
        return ollamaProvider;
    }

    public AIResponse route(AIRequest request) {
        AIProvider provider = selectProvider(request.getTaskType());
        log.debug("Routing taskType={} to provider={}", request.getTaskType(), provider.getProviderName());
        String content = provider.complete(request.getPrompt(), request.getConfig());
        return AIResponse.builder()
                .responseId(UUID.randomUUID())
                .requestId(request.getRequestId())
                .content(content)
                .providerName(provider.getProviderName())
                .build();
    }
}
