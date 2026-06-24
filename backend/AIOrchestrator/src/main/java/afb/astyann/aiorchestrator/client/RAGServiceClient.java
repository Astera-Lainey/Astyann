package afb.astyann.aiorchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "rag-service", url = "${services.rag.url:http://localhost:8091}")
public interface RAGServiceClient {

    @GetMapping("/api/v1/rag/context")
    String retrieveContext(@RequestParam UUID projectId,
                           @RequestParam String query,
                           @RequestParam int topK);

    @GetMapping("/api/v1/rag/sources")
    List<String> getSources(@RequestParam UUID projectId,
                            @RequestParam String query);
}
