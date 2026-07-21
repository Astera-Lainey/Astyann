package afb.astyann.codegeneration.client;

import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "requirement-service", url = "${services.requirement.url:http://localhost:8083}")
public interface RequirementServiceClient {

    @GetMapping("/api/v1/requirements/{projectId}/pcsf")
    ApiResponse<Pcsf> getPcsf(@PathVariable("projectId") UUID projectId);

    @GetMapping("/api/v1/requirements/{projectId}/pcsf/status")
    ApiResponse<PcsfStatusPayload> getPcsfStatus(@PathVariable("projectId") UUID projectId);

    record PcsfStatusPayload(String pcsfStatus, double completenessScore, int pendingQuestionsCount) {}
}
