package afb.astyann.documentservice.controller;

import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import afb.astyann.documentservice.dto.ApiResponse;
import afb.astyann.documentservice.dto.ApproveDocumentRequest;
import afb.astyann.documentservice.dto.ApproveDocumentResponse;
import afb.astyann.documentservice.dto.ChangeRequestBody;
import afb.astyann.documentservice.dto.ChangeRequestResponse;
import afb.astyann.documentservice.dto.DocumentListData;
import afb.astyann.documentservice.dto.DocumentListItemDTO;
import afb.astyann.documentservice.dto.DocumentSummaryDTO;
import afb.astyann.documentservice.dto.GenerateDocumentsData;
import afb.astyann.documentservice.dto.GenerateDocumentsRequest;
import afb.astyann.documentservice.dto.RegenerateDocumentRequest;
import afb.astyann.documentservice.service.DocumentGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentGenerationService service;

    /**
     * Starts generation and returns immediately with every requested document type in
     * GENERATING status — it does not wait for the AI+merge pipeline to finish. Poll
     * GET /{projectId} until no document is left in GENERATING to find out how each one
     * turned out (PENDING_APPROVAL or FAILED, with lastError set on failure).
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<GenerateDocumentsData>> generate(
            @PathVariable String projectId,
            @RequestBody(required = false) GenerateDocumentsRequest body) {
        UUID id = parseId(projectId);
        List<Document> placeholders = service.startGeneration(
                id, body != null ? body.getDocumentTypes() : null);

        List<DocumentSummaryDTO> dtos = placeholders.stream().map(this::toSummary).toList();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<GenerateDocumentsData>builder()
                        .status(202)
                        .message("Document generation started.")
                        .data(GenerateDocumentsData.builder().documents(dtos).build())
                        .build());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<DocumentListData>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(required = false) DocumentStatus status) {
        List<DocumentListItemDTO> dtos = service.getDocuments(parseId(projectId), type, status).stream()
                .map(this::toListItem)
                .toList();
        return ResponseEntity.ok(ApiResponse.<DocumentListData>builder()
                .status(200)
                .message("Documents retrieved.")
                .data(DocumentListData.builder().documents(dtos).build())
                .build());
    }

    @GetMapping("/{projectId}/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String projectId,
            @PathVariable String documentId) {
        byte[] bytes = service.downloadDocument(parseId(projectId), parseId(documentId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                .body(bytes);
    }

    /**
     * True rollback — restores an archived (previously-approved) version as the document's
     * current live file, so download immediately reflects it. Also flips the active flag on the
     * corresponding VersionService snapshot. Distinct from VersionService's own POST
     * /snapshots/{snapId}/activate, which only flips that flag and never touches this document's
     * actual content.
     */
    @PostMapping("/{projectId}/{documentId}/versions/{snapshotId}/activate")
    public ResponseEntity<ApiResponse<DocumentSummaryDTO>> activateVersion(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @PathVariable String snapshotId) {
        Document restored = service.activateVersion(parseId(projectId), parseId(documentId), parseId(snapshotId));
        return ResponseEntity.ok(ApiResponse.<DocumentSummaryDTO>builder()
                .status(200)
                .message("Document version restored.")
                .data(toSummary(restored))
                .build());
    }

    @PostMapping("/{projectId}/{documentId}/approve")
    public ResponseEntity<ApiResponse<ApproveDocumentResponse>> approve(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @RequestBody(required = false) ApproveDocumentRequest body) {
        String note = body != null ? body.getValidationNote() : null;
        DocumentGenerationService.ApproveOutcome outcome =
                service.approveDocument(parseId(projectId), parseId(documentId), note);
        return ResponseEntity.ok(ApiResponse.<ApproveDocumentResponse>builder()
                .status(200)
                .message("Document validated.")
                .data(ApproveDocumentResponse.builder()
                        .status(outcome.document().getStatus().name())
                        .validationReport(outcome.report())
                        .allDocumentsApproved(outcome.allDocumentsApproved())
                        .build())
                .build());
    }

    @PostMapping("/{projectId}/{documentId}/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> changeRequest(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @Valid @RequestBody ChangeRequestBody body) {
        Document doc = service.submitChangeRequest(parseId(projectId), parseId(documentId), body.getInstructions());
        return ResponseEntity.ok(ApiResponse.<ChangeRequestResponse>builder()
                .status(200)
                .message("Change request recorded. Call regenerate to apply it.")
                .data(ChangeRequestResponse.builder()
                        .changeRequestId(UUID.randomUUID())
                        .status(doc.getStatus().name())
                        .build())
                .build());
    }

    /**
     * Starts regeneration and returns immediately with the document in GENERATING status — it
     * does not wait for the AI+merge pipeline to finish. Poll GET /{projectId} until the document
     * is no longer GENERATING to find out the outcome (PENDING_APPROVAL or FAILED).
     */
    @PostMapping("/{projectId}/{documentId}/regenerate")
    public ResponseEntity<ApiResponse<DocumentSummaryDTO>> regenerate(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @RequestBody(required = false) RegenerateDocumentRequest body) {
        DocumentGenerationService.RegenerateResult result =
                service.regenerateDocument(parseId(projectId), parseId(documentId));
        DocumentSummaryDTO dto = toSummary(result.document());
        dto.setPreviousVersionId(result.previousVersionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<DocumentSummaryDTO>builder()
                        .status(202)
                        .message("Document regeneration started.")
                        .data(dto)
                        .build());
    }

    private DocumentSummaryDTO toSummary(Document d) {
        return DocumentSummaryDTO.builder()
                .documentId(d.getDocumentId())
                .type(d.getType())
                .status(d.getStatus())
                .pageCount(d.getPageCount())
                .lastError(d.getLastError())
                .build();
    }

    private DocumentListItemDTO toListItem(Document d) {
        return DocumentListItemDTO.builder()
                .documentId(d.getDocumentId())
                .type(d.getType())
                .status(d.getStatus())
                .version(d.getVersion())
                .generatedAt(d.getGeneratedDate())
                .lastError(d.getLastError())
                .build();
    }

    private UUID parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("Id is missing");
        }
        String s = rawId.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
