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

import java.util.ArrayList;
import java.util.Arrays;
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
     * <p>Layer selection is optional and can come from either the body
     * ({@code { "layers": ["BACKEND"] }}) or the query string ({@code ?layer=BACKEND}, repeatable,
     * or {@code ?layers=BACKEND,FRONTEND}). Specifying neither generates every layer.
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<GenerateCodeData>> generate(
            @PathVariable String projectId,
            @RequestParam(name = "layer", required = false) List<CodeLayer> layerParam,
            @RequestParam(name = "layers", required = false) List<CodeLayer> layersParam,
            @RequestBody(required = false) GenerateCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = resolveLayers(body != null ? body.getLayers() : null,
                layerParam, layersParam);
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

    /**
     * Validates the generated code. Pass {@code ?layer=BACKEND} (repeatable, or a comma-separated
     * {@code ?layers=BACKEND,FRONTEND}) to validate a single layer; omit it to validate all.
     * The response echoes {@code layersValidated} so the effective scope is never ambiguous.
     */
    @PostMapping("/{projectId}/validate")
    public ResponseEntity<ApiResponse<ValidationReportDTO>> validate(
            @PathVariable String projectId,
            @RequestParam(name = "layer", required = false) List<CodeLayer> layerParam,
            @RequestParam(name = "layers", required = false) List<CodeLayer> layersParam) {
        List<CodeLayer> layers = resolveLayers(null, layerParam, layersParam);
        ValidationReportDTO report = service.validate(parseId(projectId), layers);
        return ResponseEntity.ok(ApiResponse.<ValidationReportDTO>builder()
                .status(200).message("Validation completed.").data(report).build());
    }

    // ── Approve ─────────────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/approve")
    public ResponseEntity<ApiResponse<ApproveCodeResponse>> approve(
            @PathVariable String projectId,
            @RequestParam(name = "layer", required = false) List<CodeLayer> layerParam,
            @RequestParam(name = "layers", required = false) List<CodeLayer> layersParam,
            @RequestBody(required = false) ApproveCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = resolveLayers(body != null ? body.getLayers() : null,
                layerParam, layersParam);
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
            @RequestParam(name = "layer", required = false) List<CodeLayer> layerParam,
            @RequestParam(name = "layers", required = false) List<CodeLayer> layersParam,
            @RequestBody(required = false) RegenerateCodeRequest body) {
        UUID id = parseId(projectId);
        List<CodeLayer> layers = resolveLayers(body != null ? body.getLayers() : null,
                layerParam, layersParam);
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

    /**
     * Merges the layer selection from the request body with the {@code ?layer=} / {@code ?layers=}
     * query parameters, de-duplicated. Returns {@code null} when nothing was specified, which the
     * service reads as "every layer".
     *
     * <p>Both spellings are accepted because {@code /download} and {@code /change-request} already
     * take a singular {@code ?layer=}; supporting only a body field on the other endpoints meant a
     * {@code ?layer=BACKEND} query param was silently ignored and every layer was processed.
     */
    private List<CodeLayer> resolveLayers(List<CodeLayer> fromBody,
                                          List<CodeLayer> layerParam,
                                          List<CodeLayer> layersParam) {
        List<CodeLayer> merged = new ArrayList<>();
        for (List<CodeLayer> source : Arrays.asList(fromBody, layerParam, layersParam)) {
            if (source == null) continue;
            for (CodeLayer layer : source) {
                if (layer != null && !merged.contains(layer)) merged.add(layer);
            }
        }
        return merged.isEmpty() ? null : merged;
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
