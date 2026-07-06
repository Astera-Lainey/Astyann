package afb.astyann.requirementservice.client;

import afb.astyann.requirementservice.dto.rag.RagBatchIndexDTO;
import afb.astyann.requirementservice.dto.rag.RagIndexItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "rag-service", url = "${services.rag.url:http://localhost:8091}")
public interface RAGServiceClient {

    @PostMapping("/api/v1/rag/index")
    void indexDocument(@RequestBody RagIndexItemDTO item);

    @PostMapping("/api/v1/rag/index/batch")
    void indexBatch(@RequestBody RagBatchIndexDTO batch);
}
