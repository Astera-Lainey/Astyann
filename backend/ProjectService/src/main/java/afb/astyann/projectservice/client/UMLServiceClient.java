package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "uml-service", url = "${services.uml.url:http://localhost:8085}")
public interface UMLServiceClient {

    @PostMapping("/api/v1/uml/{projectId}/trigger")
    void triggerUMLGeneration(@PathVariable UUID projectId);
}
