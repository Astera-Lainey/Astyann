package afb.astyann.codegeneration.controller;

import afb.astyann.codegeneration.domain.CodeLayer;
import afb.astyann.codegeneration.domain.GeneratedCode;
import afb.astyann.codegeneration.dto.ApiResponse;
import afb.astyann.codegeneration.dto.ApproveCodeRequest;
import afb.astyann.codegeneration.dto.ApproveCodeResponse;
import afb.astyann.codegeneration.dto.ChangeRequestBody;
import afb.astyann.codegeneration.dto.ChangeRequestResponse;
import afb.astyann.codegeneration.dto.GenerateCodeData;
import afb.astyann.codegeneration.dto.GenerateCodeRequest;
import afb.astyann.codegeneration.dto.GeneratedCodeDTO;
import afb.astyann.codegeneration.dto.RegenerateCodeRequest;
import afb.astyann.codegeneration.dto.ValidationReportDTO;
import afb.astyann.codegeneration.service.CodeGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/code")
@RequiredArgsConstructor
public class CodeGenController {

    private final CodeGenerationService service;

    // ── Generate ────────────────────────────────────────────────────────────────

    /**
     * Starts code generation and returns immediately with each selected layer in GENERATING
     * status — it does not wait for the template/ZIP pipeline to finish. Poll GET /{projectId}
     * until no layer is left in GENERATING to find out the outcome per layer (GENERATED or
     * FAILED).
     *
     * <p>Body is optional: no body / empty body / {@code { "layers": [] }} generates every
     * layer. Pass {@code { "layers": ["BACKEND"] }} to generate a single layer.
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<GenerateCodeData>> generate(
            @PathVariable String projectId,
            @RequestBody(required = false) GenerateCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = body != null ? body.getLayers() : null;
        List<GeneratedCodeDTO> artifacts = service.generate(id, layers).stream().map(this::toDto).toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<GenerateCodeData>builder()
                        .status(202)
                        .message("Code generation started.")
                        .data(GenerateCodeData.builder().artifacts(artifacts).build())
                        .build());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<GenerateCodeData>> list(@PathVariable String projectId) {
        UUID id = parseId(projectId);
        List<GeneratedCodeDTO> artifacts = service.getGeneratedCode(id).stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.<GenerateCodeData>builder()
                .status(200)
                .message("Generated code retrieved.")
                .data(GenerateCodeData.builder().artifacts(artifacts).build())
                .build());
    }

    @GetMapping("/{projectId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String projectId,
                                           @RequestParam(defaultValue = "BACKEND") CodeLayer layer) {
        UUID id = parseId(projectId);
        byte[] zip = service.downloadCode(id, layer);
        String filename = layer.name().toLowerCase() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    // ── Validate ────────────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/validate")
    public ResponseEntity<ApiResponse<ValidationReportDTO>> validate(@PathVariable String projectId) {
        ValidationReportDTO report = service.validate(parseId(projectId));
        return ResponseEntity.ok(ApiResponse.<ValidationReportDTO>builder()
                .status(200).message("Validation completed.").data(report).build());
    }

    // ── Approve ─────────────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/approve")
    public ResponseEntity<ApiResponse<ApproveCodeResponse>> approve(
            @PathVariable String projectId,
            @RequestBody(required = false) ApproveCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = body != null ? body.getLayers() : null;
        String comment = body != null ? body.getApprovalComment() : null;
        CodeGenerationService.ApproveOutcome outcome = service.approve(id, layers, comment);
        return ResponseEntity.ok(ApiResponse.<ApproveCodeResponse>builder()
                .status(200).message("Code approved.")
                .data(ApproveCodeResponse.builder()
                        .snapshotIds(outcome.snapshotIds())
                        .updatedCount(outcome.updatedCount())
                        .allLayersApproved(outcome.allLayersApproved())
                        .build())
                .build());
    }

    // ── Change request ──────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> changeRequest(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "BACKEND") CodeLayer layer,
            @Valid @RequestBody ChangeRequestBody body) {
        GeneratedCode code = service.submitChangeRequest(parseId(projectId), layer, body.getInstructions());
        return ResponseEntity.ok(ApiResponse.<ChangeRequestResponse>builder()
                .status(200)
                .message("Change request recorded. Call regenerate to apply it.")
                .data(ChangeRequestResponse.builder()
                        .changeRequestId(UUID.randomUUID())
                        .status(code.getStatus().name())
                        .build())
                .build());
    }

    // ── Regenerate ──────────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/regenerate")
    public ResponseEntity<ApiResponse<GenerateCodeData>> regenerate(
            @PathVariable String projectId,
            @RequestBody(required = false) RegenerateCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = body != null ? body.getLayers() : null;
        CodeGenerationService.RegenerateResult result = service.regenerate(id, layers);
        List<GeneratedCodeDTO> dtos = result.layers().stream().map(this::toDto).toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<GenerateCodeData>builder()
                        .status(202)
                        .message("Code regeneration started.")
                        .data(GenerateCodeData.builder().artifacts(dtos).build())
                        .build());
    }

    // ── Activate archived version ───────────────────────────────────────────────

    @PostMapping("/{projectId}/versions/{snapshotId}/activate")
    public ResponseEntity<ApiResponse<GeneratedCodeDTO>> activateVersion(
            @PathVariable String projectId,
            @PathVariable String snapshotId,
            @RequestParam(defaultValue = "BACKEND") CodeLayer layer) {
        GeneratedCode restored = service.activateVersion(parseId(projectId), layer, parseId(snapshotId));
        return ResponseEntity.ok(ApiResponse.<GeneratedCodeDTO>builder()
                .status(200).message("Code version restored.").data(toDto(restored)).build());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private GeneratedCodeDTO toDto(GeneratedCode code) {
        return GeneratedCodeDTO.builder()
                .codeId(code.getCodeId())
                .layer(code.getLayer())
                .status(code.getStatus())
                .downloadUrl(code.getDownloadUrl())
                .lastError(code.getLastError())
                .genDate(code.getGenDate())
                .modulesTotal(code.getModulesTotal())
                .modulesPatched(code.getModulesPatched())
                .stubMethodsRemaining(code.getStubMethodsRemaining())
                .build();
    }

    private UUID parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("projectId is missing");
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
