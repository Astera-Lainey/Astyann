package afb.astyann.documentservice.client;

import afb.astyann.documentservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "version-service", url = "${services.version.url:http://localhost:8087}")
public interface VersionServiceClient {

    @PostMapping("/api/v1/versions/{projectId}/snapshots")
    ApiResponse<SnapshotDTO> createSnapshot(@PathVariable("projectId") UUID projectId,
                                             @RequestBody CreateSnapshotRequest body);

    @GetMapping("/api/v1/versions/{projectId}/snapshots")
    ApiResponse<List<SnapshotDTO>> listSnapshots(@PathVariable("projectId") UUID projectId);

    record CreateSnapshotRequest(String artifactType, String versionName, UUID entrySource,
                                  String triggerReason, String artifactPath,
                                  UUID documentId, String documentType) {}

    record SnapshotDTO(UUID snapId, UUID timelineId, Integer versionNumber, String artifactType,
                        boolean active, UUID documentId, String documentType) {}
}
