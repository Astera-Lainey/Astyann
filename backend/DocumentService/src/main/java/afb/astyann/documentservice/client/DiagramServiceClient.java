package afb.astyann.documentservice.client;

import afb.astyann.documentservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "diagram-service", url = "${services.diagram.url:http://localhost:8084}")
public interface DiagramServiceClient {

    @GetMapping("/api/v1/uml/{projectId}")
    ApiResponse<DiagramListData> list(@PathVariable("projectId") UUID projectId);

    @GetMapping("/api/v1/uml/{projectId}/{diagramId}/render")
    byte[] render(@PathVariable("projectId") UUID projectId,
                  @PathVariable("diagramId") UUID diagramId,
                  @RequestParam("format") String format);

    record DiagramListData(List<DiagramItem> diagrams) {}
    record DiagramItem(UUID diagramId, String type, String status) {}
}
