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
            @PathVariable String projectId,
            @RequestBody(required = false) GenerateDiagramsRequest body) {
        List<UMLDiagram> diagrams = service.generateDiagrams(
                parseId(projectId), body != null ? body : new GenerateDiagramsRequest());
        List<DiagramSummaryDTO> dtos = diagrams.stream().map(d -> toSummary(parseId(projectId), d)).toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<GenerateDiagramsData>builder()
                        .status(201)
                        .message("Diagrams generated.")
                        .data(GenerateDiagramsData.builder().diagrams(dtos).build())
                        .build());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<DiagramListData>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) DiagramType type,
            @RequestParam(required = false) DiagramStatus status) {
        List<DiagramListItemDTO> dtos = service.getDiagrams(parseId(projectId), type, status).stream()
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
            @PathVariable String projectId,
            @PathVariable String diagramId,
            @RequestParam(defaultValue = "SVG") String format) {
        byte[] bytes = service.renderDiagram(parseId(projectId), parseId(diagramId), format);
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

    private UUID parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header is missing");
        }
        String s = rawId.trim();
        // Strip 0x prefix if present
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        // Insert dashes if raw 32-char hex (no dashes)
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
