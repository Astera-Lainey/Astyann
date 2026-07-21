package afb.astyann.codegeneration.client;

import afb.astyann.codegeneration.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "document-service", url = "${services.document.url:http://localhost:8085}")
public interface DocumentServiceClient {

    @GetMapping("/api/v1/documents/{projectId}")
    ApiResponse<DocumentListData> list(@PathVariable("projectId") UUID projectId);

    record DocumentListData(List<DocumentItem> documents) {}
    record DocumentItem(UUID documentId, String type, String status) {}
}
