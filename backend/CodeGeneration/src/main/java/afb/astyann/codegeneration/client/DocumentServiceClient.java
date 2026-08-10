package afb.astyann.codegeneration.client;

import afb.astyann.codegeneration.dto.ApiResponse;
import afb.astyann.codegeneration.dto.DocumentContentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "document-service", url = "${services.document.url:http://localhost:8085}")
public interface DocumentServiceClient {

    @GetMapping("/api/v1/documents/{projectId}")
    ApiResponse<DocumentListData> list(@PathVariable("projectId") UUID projectId);

    /**
     * The approved document of one type, as structured JSON.
     *
     * <p>404 when no approved document of that type exists, 409 when one exists but predates
     * content persistence — both are tolerated by the caller, which degrades to generating without
     * that document rather than failing.
     */
    @GetMapping("/api/v1/documents/{projectId}/content")
    ApiResponse<DocumentContentDTO> approvedContent(@PathVariable("projectId") UUID projectId,
                                                    @RequestParam("type") String type);

    record DocumentListData(List<DocumentItem> documents) {}
    record DocumentItem(UUID documentId, String type, String status) {}
}
