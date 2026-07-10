package afb.astyann.requirementservice.controller;

import afb.astyann.requirementservice.domain.ClarificationQuestion;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.dto.*;
import afb.astyann.requirementservice.dto.pcsf.*;
import afb.astyann.requirementservice.exception.RequirementNotFoundException;
import afb.astyann.requirementservice.repository.RequirementRepository;
import afb.astyann.requirementservice.service.RequirementBackgroundService;
import afb.astyann.requirementservice.service.RequirementGenerationService;
import afb.astyann.requirementservice.service.pcsf.PcsfValidationService;
import afb.astyann.requirementservice.service.pcsf.QAService;
import afb.astyann.requirementservice.util.PcsfFieldWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementRepository       requirementRepository;
    private final RequirementBackgroundService backgroundService;
    private final RequirementGenerationService generationService;
    private final QAService                   qaService;
    private final PcsfValidationService       validationService;
    private final PcsfFieldWriter             fieldWriter;
    private final ObjectMapper                objectMapper;

    // ── API-REQ-01 ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/requirements/{projectId}/initialize
     * Called by ProjectService after document analysis to start the PCSF pipeline.
     */
    @PostMapping("/{projectId}/initialize")
    public ResponseEntity<ApiResponse<Void>> initialize(
            @PathVariable String projectId,
            @RequestBody RequirementInitRequest body) {

        if (requirementRepository.existsByProjectId(parseId(projectId))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<Void>builder()
                            .status(409).message("Requirements pipeline already initialized for this project.")
                            .build());
        }

        backgroundService.initializePipelineAsync(
                parseId(projectId),
                body.getProjectTitle(),
                body.getProjectDescription(),
                body.getProjectContext(),
                body.getDocumentText());

        return ResponseEntity.accepted()
                .body(ApiResponse.<Void>builder()
                        .status(202).message("Requirement pipeline started.").build());
    }

    // ── API-REQ-02: Q&A ────────────────────────────────────────────────────────

    /**
     * GET /api/v1/requirements/{projectId}/questions
     */
    @GetMapping("/{projectId}/questions")
    public ResponseEntity<ApiResponse<List<ClarificationQuestionDto>>> getQuestions(
            @PathVariable String projectId) {
        List<ClarificationQuestion> questions = qaService.getPendingQuestions(parseId(projectId));
        List<ClarificationQuestionDto> dtos = questions.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<ClarificationQuestionDto>>builder()
                .status(200).message("Questions retrieved.").data(dtos).build());
    }

    /**
     * POST /api/v1/requirements/{projectId}/questions/answers
     */
    @PostMapping("/{projectId}/questions/answers")
    public ResponseEntity<ApiResponse<QAResponse>> submitAnswers(
            @PathVariable String projectId,
            @RequestBody SubmitAnswersRequest request) {
        QAResponse response = qaService.submitAnswers(parseId(projectId), request);
        return ResponseEntity.ok(ApiResponse.<QAResponse>builder()
                .status(200).message("Answers submitted.").data(response).build());
    }

    // ── PCSF inspection ────────────────────────────────────────────────────────

    /**
     * GET /api/v1/requirements/{projectId}/pcsf
     */
    @GetMapping("/{projectId}/pcsf")
    public ResponseEntity<ApiResponse<Pcsf>> getPcsf(@PathVariable String projectId) {
        Requirement req = findOrThrow(parseId(projectId));
        if (req.getPcsfJson() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Pcsf>builder().status(404).message("PCSF not yet initialised.").build());
        }
        try {
            Pcsf pcsf = objectMapper.readValue(req.getPcsfJson(), Pcsf.class);
            return ResponseEntity.ok(ApiResponse.<Pcsf>builder()
                    .status(200).message("PCSF retrieved.").data(pcsf).build());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Pcsf>builder()
                            .status(500).message("Failed to parse PCSF: " + ex.getMessage()).build());
        }
    }

    /**
     * PATCH /api/v1/requirements/{projectId}/pcsf/fields
     */
    @PatchMapping("/{projectId}/pcsf/fields")
    public ResponseEntity<ApiResponse<Void>> patchField(
            @PathVariable String projectId,
            @Valid @RequestBody PatchFieldRequest request) {
        Requirement req = findOrThrow(parseId(projectId));
        if (req.getPcsfJson() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Void>builder().status(404).message("PCSF not found.").build());
        }
        try {
            Pcsf pcsf = objectMapper.readValue(req.getPcsfJson(), Pcsf.class);
            fieldWriter.write(pcsf, request.getPath(), request.getValue());
            req.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            requirementRepository.save(req);
            return ResponseEntity.ok(ApiResponse.<Void>builder().status(200).message("Field updated.").build());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder().status(500).message("Patch failed: " + ex.getMessage()).build());
        }
    }

    /**
     * GET /api/v1/requirements/{projectId}/pcsf/status
     */
    @GetMapping("/{projectId}/pcsf/status")
    public ResponseEntity<ApiResponse<PcsfStatusResponse>> getPcsfStatus(@PathVariable String projectId) {
        Requirement req = findOrThrow(parseId(projectId));
        double score = 0.0;
        if (req.getPcsfJson() != null) {
            try {
                Pcsf pcsf = objectMapper.readValue(req.getPcsfJson(), Pcsf.class);
                if (pcsf.getValidation() != null) score = pcsf.getValidation().getCompletenessScore();
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.<PcsfStatusResponse>builder()
                .status(200).message("Status retrieved.")
                .data(PcsfStatusResponse.builder()
                        .pcsfStatus(req.getPcsfStatus().name())
                        .completenessScore(score)
                        .pendingQuestionsCount(req.getPendingQuestionsCount() != null
                                ? req.getPendingQuestionsCount() : 0)
                        .build())
                .build());
    }

    /**
     * POST /api/v1/requirements/{projectId}/pcsf/validate
     */
    @PostMapping("/{projectId}/pcsf/validate")
    public ResponseEntity<ApiResponse<PcsfValidateResponse>> validatePcsf(@PathVariable String projectId) {
        PcsfValidateResponse result = validationService.validate(parseId(projectId));
        int status = result.isValid() ? 200 : 422;
        return ResponseEntity.status(status)
                .body(ApiResponse.<PcsfValidateResponse>builder()
                        .status(status)
                        .message(result.isValid() ? "PCSF validated and locked." : "Validation failed.")
                        .data(result).build());
    }

    // ── API-REQ-03: Approve ────────────────────────────────────────────────────

    /**
     * POST /api/v1/requirements/{projectId}/approve
     */
    @PostMapping("/{projectId}/approve")
    public ResponseEntity<ApiResponse<ApproveResponse>> approve(@PathVariable String projectId) {
        ApproveResponse result = generationService.approve(parseId(projectId));
        return ResponseEntity.ok(ApiResponse.<ApproveResponse>builder()
                .status(200).message(result.getMessage()).data(result).build());
    }

    // ── API-REQ-04: Change request ─────────────────────────────────────────────

    /**
     * POST /api/v1/requirements/{projectId}/change-request
     */
    @PostMapping("/{projectId}/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> changeRequest(
            @PathVariable String projectId,
            @Valid @RequestBody ChangeRequestBody body) {
        ChangeRequestResponse result = generationService.submitChangeRequest(parseId(projectId), body.getInstructions());
        return ResponseEntity.ok(ApiResponse.<ChangeRequestResponse>builder()
                .status(200).message(result.getMessage()).data(result).build());
    }

    // ── API-REQ-05: Regenerate ─────────────────────────────────────────────────

    /**
     * POST /api/v1/requirements/{projectId}/regenerate
     */
    @PostMapping("/{projectId}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerate(@PathVariable String projectId) {
        generationService.regenerate(parseId(projectId));
        return ResponseEntity.accepted()
                .body(ApiResponse.<Void>builder()
                        .status(202).message("Regeneration started. Poll /pcsf/status for updates.").build());
    }

    // ── Retry after FAILED (inference stage only) ──────────────────────────────

    /**
     * POST /api/v1/requirements/{projectId}/retry
     * Retries the AI-inference pipeline after pcsfStatus === FAILED. Only covers failures
     * during INF-1/3/4 — if the PCSF was never created, generationService throws and the
     * client is told to start a new project instead.
     */
    @PostMapping("/{projectId}/retry")
    public ResponseEntity<ApiResponse<Void>> retry(@PathVariable String projectId) {
        generationService.retryInference(parseId(projectId));
        return ResponseEntity.accepted()
                .body(ApiResponse.<Void>builder()
                        .status(202).message("Retry started. Poll /pcsf/status for updates.").build());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Requirement findOrThrow(UUID projectId) {
        return requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));
    }

    private ClarificationQuestionDto toDto(ClarificationQuestion q) {
        List<String> options = null;
        if (q.getOptionsJson() != null) {
            try {
                options = Arrays.asList(objectMapper.readValue(q.getOptionsJson(), String[].class));
            } catch (Exception ignored) {}
        }
        return ClarificationQuestionDto.builder()
                .id(q.getId()).inventoryRef(q.getInventoryRef()).targetPath(q.getTargetPath())
                .priority(q.getPriority()).question(q.getQuestion()).type(q.getType())
                .options(options).placeholder(q.getPlaceholder())
                .answered(q.isAnswered()).answer(q.getAnswer())
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
