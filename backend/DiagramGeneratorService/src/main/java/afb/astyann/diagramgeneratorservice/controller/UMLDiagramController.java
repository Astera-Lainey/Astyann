package afb.astyann.diagramgeneratorservice.controller;

import afb.astyann.diagramgeneratorservice.domain.DiagramStatus;
import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import afb.astyann.diagramgeneratorservice.dto.ApiResponse;
import afb.astyann.diagramgeneratorservice.dto.DiagramListData;
import afb.astyann.diagramgeneratorservice.dto.DiagramListItemDTO;
import afb.astyann.diagramgeneratorservice.dto.DiagramSummaryDTO;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsData;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsRequest;
import afb.astyann.diagramgeneratorservice.service.DiagramGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uml")
@RequiredArgsConstructor
public class UMLDiagramController {

    private final DiagramGenerationService service;

    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<GenerateDiagramsData>> generate(
            @PathVariable UUID projectId,
            @RequestBody(required = false) GenerateDiagramsRequest body) {
        List<UMLDiagram> diagrams = service.generateDiagrams(
                projectId, body != null ? body : new GenerateDiagramsRequest());
        List<DiagramSummaryDTO> dtos = diagrams.stream().map(d -> toSummary(projectId, d)).toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<GenerateDiagramsData>builder()
                        .status(201)
                        .message("Diagrams generated.")
                        .data(GenerateDiagramsData.builder().diagrams(dtos).build())
                        .build());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<DiagramListData>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) DiagramType type,
            @RequestParam(required = false) DiagramStatus status) {
        List<DiagramListItemDTO> dtos = service.getDiagrams(projectId, type, status).stream()
                .map(this::toListItem)
                .toList();
        return ResponseEntity.ok(ApiResponse.<DiagramListData>builder()
                .status(200)
                .message("Diagrams retrieved.")
                .data(DiagramListData.builder().diagrams(dtos).build())
                .build());
    }

    @GetMapping("/{projectId}/{diagramId}/render")
    public ResponseEntity<byte[]> render(
            @PathVariable UUID projectId,
            @PathVariable UUID diagramId,
            @RequestParam(defaultValue = "SVG") String format) {
        byte[] bytes = service.renderDiagram(projectId, diagramId, format);
        MediaType mediaType = "PNG".equalsIgnoreCase(format)
                ? MediaType.IMAGE_PNG
                : MediaType.valueOf("image/svg+xml");
        return ResponseEntity.ok().contentType(mediaType).body(bytes);
    }

    private DiagramSummaryDTO toSummary(UUID projectId, UMLDiagram diagram) {
        return DiagramSummaryDTO.builder()
                .diagramId(diagram.getDiagramId())
                .type(diagram.getType())
                .status(diagram.getStatus())
                .renderUrl("/api/v1/uml/" + projectId + "/" + diagram.getDiagramId() + "/render")
                .build();
    }

    private DiagramListItemDTO toListItem(UMLDiagram diagram) {
        return DiagramListItemDTO.builder()
                .diagramId(diagram.getDiagramId())
                .type(diagram.getType())
                .status(diagram.getStatus())
                .updatedAt(diagram.getUpdatedAt())
                .build();
    }
}
