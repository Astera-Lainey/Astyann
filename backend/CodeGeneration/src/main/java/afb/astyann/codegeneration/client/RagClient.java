package afb.astyann.codegeneration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Semantic-search context lookup against the RAG service. Returns a plain-text blob of the
 * top-k passages (from documents, diagrams, prior code) most relevant to the query, scoped to
 * the given project.
 *
 * <p>Implemented with {@link RestClient} rather than Feign: the Feign decoder for a
 * {@code String}-returning endpoint failed with {@code 'messageConverters' must not be empty}
 * on this stack. RestClient ships its own default converters, so it avoids that wiring entirely.
 * The lookup is best-effort — callers ({@code LogicInjectionService}) already swallow failures
 * and continue without RAG context — so a short timeout keeps a dead RAG service from stalling
 * code generation.
 */
@Component
@Slf4j
public class RagClient {

    private final RestClient restClient;

    public RagClient(@Value("${services.rag.url:http://localhost:8090}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * @param topK how many passages to retrieve. The parameter name must stay in sync with
     *             {@code RAGController.getContext}, which binds it as {@code topK} — an unknown
     *             query parameter is silently ignored there and the endpoint's own default is used
     *             instead, so a mismatch makes {@code codegen.ai.rag.top-k} a dead knob rather than
     *             an error.
     */
    public String getContext(UUID projectId, String query, int topK) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/rag/context")
                        .queryParam("projectId", projectId)
                        .queryParam("query", query)
                        .queryParam("topK", topK)
                        .build())
                .retrieve()
                .body(String.class);
    }
}
