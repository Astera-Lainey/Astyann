package afb.astyann.diagramgeneratorservice.controller;

import afb.astyann.diagramgeneratorservice.domain.DiagramStatus;
import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import afb.astyann.diagramgeneratorservice.dto.ApiResponse;
import afb.astyann.diagramgeneratorservice.dto.ApproveDiagramRequest;
import afb.astyann.diagramgeneratorservice.dto.ApproveDiagramsRequest;
import afb.astyann.diagramgeneratorservice.dto.ApproveDiagramsResponse;
import afb.astyann.diagramgeneratorservice.dto.ChangeRequestBody;
import afb.astyann.diagramgeneratorservice.dto.ChangeRequestResponse;
import afb.astyann.diagramgeneratorservice.dto.DiagramListData;
import afb.astyann.diagramgeneratorservice.dto.DiagramListItemDTO;
import afb.astyann.diagramgeneratorservice.dto.DiagramSummaryDTO;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsData;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsRequest;
import afb.astyann.diagramgeneratorservice.dto.RegenerateDiagramRequest;
import afb.astyann.diagramgeneratorservice.service.DiagramGenerationService;
import jakarta.validation.Valid;
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

    /**
     * Starts generation and returns immediately with every requested diagram type in
     * GENERATING status — it does not wait for the AI+Kroki pipelines to finish. Poll
     * GET /{projectId} until no diagram is left in GENERATING to find out how each one
     * turned out (PENDING_APPROVAL or FAILED, with lastError set on failure).
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<GenerateDiagramsData>> generate(
            @PathVariable String projectId,
            @RequestBody(required = false) GenerateDiagramsRequest body) {
        UUID id = parseId(projectId);
        List<UMLDiagram> placeholders = service.startGeneration(
                id, body != null ? body : new GenerateDiagramsRequest());

        List<DiagramSummaryDTO> dtos = placeholders.stream().map(d -> toSummary(id, d)).toList();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<GenerateDiagramsData>builder()
                        .status(202)
                        .message("Diagram generation started.")
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

    /**
     * Starts regeneration and returns immediately with the diagram in GENERATING status — it
     * does not wait for the AI+Kroki pipeline to finish. Poll GET /{projectId} until the diagram
     * is no longer GENERATING to find out the outcome (PENDING_APPROVAL or FAILED).
     */
    @PostMapping("/{projectId}/{diagramId}/regenerate")
    public ResponseEntity<ApiResponse<DiagramSummaryDTO>> regenerate(
            @PathVariable String projectId,
            @PathVariable String diagramId,
            @RequestBody(required = false) RegenerateDiagramRequest body) {
        UUID pId = parseId(projectId);
        UUID dId = parseId(diagramId);
        String formatOverride = body != null ? body.getRenderFormat() : null;
        DiagramGenerationService.RegenerateResult result = service.regenerateDiagram(pId, dId, formatOverride);
        DiagramSummaryDTO dto = toSummary(pId, result.diagram());
        dto.setPreviousVersionId(result.previousVersionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<DiagramSummaryDTO>builder()
                        .status(202)
                        .message("Diagram regeneration started.")
                        .data(dto)
                        .build());
    }

    @PostMapping("/{projectId}/approve")
    public ResponseEntity<ApiResponse<ApproveDiagramsResponse>> approve(
            @PathVariable String projectId,
            @RequestBody(required = false) ApproveDiagramsRequest body) {
        UUID pId = parseId(projectId);
        List<UUID> diagramIds = body != null ? body.getDiagramIds() : null;
        String approvalComment = body != null ? body.getApprovalComment() : null;
        DiagramGenerationService.ApproveOutcome outcome = service.approve(pId, diagramIds, approvalComment);
        return ResponseEntity.ok(ApiResponse.<ApproveDiagramsResponse>builder()
                .status(200)
                .message("Diagrams approved.")
                .data(ApproveDiagramsResponse.builder()
                        .snapshotIds(outcome.snapshotIds())
                        .updatedCount(outcome.updatedCount())
                        .allDiagramsApproved(outcome.allDiagramsApproved())
                        .build())
                .build());
    }

    @PostMapping("/{projectId}/{diagramId}/approve")
    public ResponseEntity<ApiResponse<DiagramSummaryDTO>> approveOne(
            @PathVariable String projectId,
            @PathVariable String diagramId,
            @RequestBody(required = false) ApproveDiagramRequest body) {
        UUID pId = parseId(projectId);
        UUID dId = parseId(diagramId);
        service.getDiagram(pId, dId); // 404 up front if the diagram doesn't belong to this project
        String approvalComment = body != null ? body.getApprovalComment() : null;
        service.approve(pId, List.of(dId), approvalComment);
        UMLDiagram approved = service.getDiagram(pId, dId);
        return ResponseEntity.ok(ApiResponse.<DiagramSummaryDTO>builder()
                .status(200)
                .message("Diagram approved.")
                .data(toSummary(pId, approved))
                .build());
    }

    @PostMapping("/{projectId}/{diagramId}/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> changeRequest(
            @PathVariable String projectId,
            @PathVariable String diagramId,
            @Valid @RequestBody ChangeRequestBody body) {
        UMLDiagram diagram = service.submitChangeRequest(parseId(projectId), parseId(diagramId), body.getInstructions());
        return ResponseEntity.ok(ApiResponse.<ChangeRequestResponse>builder()
                .status(200)
                .message("Change request recorded. Call regenerate to apply it.")
                .data(ChangeRequestResponse.builder()
                        .changeRequestId(UUID.randomUUID())
                        .status(diagram.getStatus().name())
                        .build())
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

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable String projectId) {
        service.deleteAllForProject(parseId(projectId));
        return ResponseEntity.noContent().build();
    }

    private DiagramSummaryDTO toSummary(UUID projectId, UMLDiagram diagram) {
        return DiagramSummaryDTO.builder()
                .diagramId(diagram.getDiagramId())
                .type(diagram.getType())
                .status(diagram.getStatus())
                .renderUrl("/api/v1/uml/" + projectId + "/" + diagram.getDiagramId() + "/render")
                .lastError(diagram.getLastError())
                .build();
    }

    private DiagramListItemDTO toListItem(UMLDiagram diagram) {
        return DiagramListItemDTO.builder()
                .diagramId(diagram.getDiagramId())
                .type(diagram.getType())
                .status(diagram.getStatus())
                .updatedAt(diagram.getUpdatedAt())
                .lastError(diagram.getLastError())
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
