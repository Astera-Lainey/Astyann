package afb.astyann.documentservice.client;

import afb.astyann.documentservice.dto.RagIndexItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "rag-service", url = "${services.rag.url:http://localhost:8090}")
public interface RAGServiceClient {

    @GetMapping("/api/v1/rag/context")
    String getContext(@RequestParam("projectId") UUID projectId,
                       @RequestParam("query") String query,
                       @RequestParam(value = "topK", required = false) Integer topK);

    @PostMapping("/api/v1/rag/index")
    void indexDocument(@RequestBody RagIndexItemDTO item);
}
