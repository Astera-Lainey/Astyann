package afb.astyann.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "version-service", url = "${services.versions.url:http://localhost:8087}")
public interface VersionServiceClient {

    @DeleteMapping("/api/v1/versions/{projectId}")
    void deleteVersions(@PathVariable UUID projectId);
}
