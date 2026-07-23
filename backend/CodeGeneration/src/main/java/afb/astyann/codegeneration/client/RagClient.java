package afb.astyann.codegeneration.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Semantic-search context lookup against the RAG service. Returns a plain-text blob of the
 * top-k passages (from documents, diagrams, prior code) most relevant to the query, scoped to
 * the given project.
 */
@FeignClient(name = "rag", url = "${services.rag.url:http://localhost:8090}")
public interface RagClient {

    @GetMapping("/api/v1/rag/context")
    String getContext(@RequestParam("projectId") UUID projectId,
                      @RequestParam("query") String query,
                      @RequestParam(value = "k", defaultValue = "5") int k);
}
