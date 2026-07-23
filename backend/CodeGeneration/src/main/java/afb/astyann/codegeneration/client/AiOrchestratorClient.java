package afb.astyann.codegeneration.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Direct model inference against the AI Orchestrator. Used by the logic-injection pass to
 * implement stub methods and by the compile self-correction loop in {@code validate()} to
 * repair compilation errors.
 */
@FeignClient(name = "ai-orchestrator", url = "${services.ai-orchestrator.url:http://localhost:8089}")
public interface AiOrchestratorClient {

    @PostMapping("/api/v1/ai/infer")
    InferenceResponse infer(@RequestBody InferenceRequest body);

    record InferenceRequest(String model, String systemPrompt, String userPrompt) {}

    record InferenceResponse(String model, String content) {}
}
