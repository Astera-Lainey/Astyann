package afb.astyann.requirementservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "project-service", url = "${services.project.url:http://localhost:8082}")
public interface ProjectServiceClient {

    @PutMapping("/api/v1/projects/{projectId}")
    void updateStatus(@PathVariable UUID projectId, @RequestBody UpdateProjectStatusRequest body);

    record UpdateProjectStatusRequest(String status) {}
}
