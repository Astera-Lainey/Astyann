package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "codegen-service", url = "${services.codegen.url:http://localhost:8086}")
public interface CodeGenServiceClient {

    @PostMapping("/api/v1/codegen/{projectId}/trigger")
    void triggerCodeGeneration(@PathVariable UUID projectId);
}
