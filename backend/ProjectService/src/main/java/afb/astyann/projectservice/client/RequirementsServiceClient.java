package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "requirements-service", url = "${services.requirements.url:http://localhost:8083}")
public interface RequirementsServiceClient {

    @PostMapping("/api/v1/requirements/{projectId}/trigger")
    void triggerRequirementsGeneration(@PathVariable UUID projectId);
}
