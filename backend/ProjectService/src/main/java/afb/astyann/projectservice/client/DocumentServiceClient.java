package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "document-service", url = "${services.document.url:http://localhost:8084}")
public interface DocumentServiceClient {

    @PostMapping("/api/v1/documents/{projectId}/trigger")
    void triggerDocumentGeneration(@PathVariable UUID projectId);
}
