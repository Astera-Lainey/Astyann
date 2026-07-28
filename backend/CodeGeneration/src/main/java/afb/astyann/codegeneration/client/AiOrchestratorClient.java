package afb.astyann.codegeneration.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Direct model inference against the AI Orchestrator. Used by the logic-injection pass to
 * implement stub methods and by the compile self-correction loop in {@code validate()} to
 * repair compilation errors.
 *
 * <p>Implemented with {@link RestClient} rather than Feign. Under the parallel logic-injection
 * pass, Feign's lazily-built {@code HttpMessageConverters} raced across threads and intermittently
 * failed with {@code no suitable HttpMessageConverter found for request type [InferenceRequest]}
 * (HTTP -1 — the request was never sent). RestClient holds its own converter list, built once at
 * construction and safe to share across threads.
 *
 * <p>The connect timeout is short, but the read timeout is generous
 * ({@code codegen.ai.read-timeout-seconds}) because model generation can take a minute or more;
 * {@code 0} means no read timeout.
 */
@Component
@Slf4j
public class AiOrchestratorClient {

    private final RestClient restClient;

    public AiOrchestratorClient(
            @Value("${services.ai-orchestrator.url:http://localhost:8089}") String baseUrl,
            @Value("${codegen.ai.read-timeout-seconds:300}") long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        if (readTimeoutSeconds > 0) factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public InferenceResponse infer(InferenceRequest body) {
        return restClient.post()
                .uri("/api/v1/ai/infer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(InferenceResponse.class);
    }

    public record InferenceRequest(String model, String systemPrompt, String userPrompt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InferenceResponse(String model, String content) {}
}
