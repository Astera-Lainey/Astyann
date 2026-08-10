package afb.astyann.documentservice.client;

import afb.astyann.documentservice.dto.ApiResponse;
import afb.astyann.documentservice.dto.pcsf.PcsfView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "requirement-service", url = "${services.requirement.url:http://localhost:8083}")
public interface RequirementServiceClient {

    @GetMapping("/api/v1/requirements/{projectId}/pcsf/status")
    ApiResponse<PcsfStatusPayload> getPcsfStatus(@PathVariable("projectId") UUID projectId);

    /**
     * The approved PCSF itself. The API contract document describes the API the code generator
     * will emit, and the generator derives that from {@code pcsf.endpoints} — so the document has
     * to read the same source rather than let a model infer endpoints from prose.
     */
    @GetMapping("/api/v1/requirements/{projectId}/pcsf")
    ApiResponse<PcsfView> getPcsf(@PathVariable("projectId") UUID projectId);

    record PcsfStatusPayload(String pcsfStatus, double completenessScore, int pendingQuestionsCount) {}
}
