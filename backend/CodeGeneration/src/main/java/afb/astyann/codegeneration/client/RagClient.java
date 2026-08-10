package afb.astyann.codegeneration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
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
        return getContext(projectId, query, topK, List.of(), Set.of());
    }

    /**
     * Retrieval narrowed to particular document types and approved snapshots.
     *
     * <p>Unscoped, a module's lookup competes for its five slots against every document, diagram
     * and requirement in the project — including chunks belonging to superseded versions. Passing
     * the document types that carry the specification, and the snapshots the approved copies came
     * from, is what makes a five-chunk budget worth spending.
     *
     * <p>Empty collections are omitted from the query, which widens the search back to the whole
     * project rather than matching nothing.
     */
    public String getContext(UUID projectId, String query, int topK,
                             List<String> documentTypes, Set<UUID> snapshotIds) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/rag/context")
                            .queryParam("projectId", projectId)
                            .queryParam("query", query)
                            .queryParam("topK", topK);
                    if (documentTypes != null && !documentTypes.isEmpty()) {
                        uriBuilder.queryParam("documentType", documentTypes.toArray());
                    }
                    if (snapshotIds != null && !snapshotIds.isEmpty()) {
                        uriBuilder.queryParam("snapshotId",
                                snapshotIds.stream().map(UUID::toString).toArray());
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(String.class);
    }
}
