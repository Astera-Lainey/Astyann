package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "deployment-service", url = "${services.deployment.url:http://localhost:8087}")
public interface DeploymentServiceClient {

    @PostMapping("/api/v1/deployment/{projectId}/trigger")
    void triggerDeploymentGeneration(@PathVariable UUID projectId);
}
