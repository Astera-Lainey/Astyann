package afb.astyann.diagramgeneratorservice.client;

import afb.astyann.diagramgeneratorservice.dto.InferenceResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-service", url = "${services.ai.url:http://localhost:8089}")
public interface AIServiceClient {

    @PostMapping("/api/v1/ai/infer")
    InferenceResponseDTO infer(@RequestBody InferBody body);

    record InferBody(String model, String systemPrompt, String userPrompt) {}
}
