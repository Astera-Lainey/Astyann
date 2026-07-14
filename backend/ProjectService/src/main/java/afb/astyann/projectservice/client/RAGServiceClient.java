package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "rag-service", url = "${services.rag.url:http://localhost:8090}")
public interface RAGServiceClient {

    @DeleteMapping("/api/v1/rag/{projectId}")
    void deleteIndex(@PathVariable UUID projectId);
}
