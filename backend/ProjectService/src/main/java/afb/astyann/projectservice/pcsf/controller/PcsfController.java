package afb.astyann.projectservice.pcsf.controller;

import afb.astyann.projectservice.domain.ClarificationQuestion;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.dto.ApiResponse;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.pcsf.dto.*;
import afb.astyann.projectservice.pcsf.model.Pcsf;
import afb.astyann.projectservice.pcsf.service.PcsfValidationService;
import afb.astyann.projectservice.pcsf.service.QAService;
import afb.astyann.projectservice.pcsf.util.PcsfFieldWriter;
import afb.astyann.projectservice.repository.ClarificationQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class PcsfController {

    private final ProjectRepository               projectRepository;
    private final ClarificationQuestionRepository questionRepository;
    private final QAService                       qaService;
    private final PcsfValidationService           validationService;
    private final PcsfFieldWriter                 fieldWriter;
    private final ObjectMapper                    objectMapper;

    /**
     * GET /api/v1/projects/{projectId}/questions
     * Returns all pending clarification questions sorted by priority.
     */
    @GetMapping("/{projectId}/questions")
    public ResponseEntity<ApiResponse<List<ClarificationQuestionDto>>> getQuestions(
            @PathVariable UUID projectId) {
        findOrThrow(projectId);
        List<ClarificationQuestionDto> questions = questionRepository
                .findByProject_ProjectIdOrderByPriorityAsc(projectId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<ClarificationQuestionDto>>builder()
                .status(200).message("Questions retrieved.").data(questions).build());
    }

    /**
     * POST /api/v1/projects/{projectId}/questions/answers
     * Submit answers and trigger completeness re-analysis.
     */
    @PostMapping("/{projectId}/questions/answers")
    public ResponseEntity<ApiResponse<QAResponse>> submitAnswers(
            @PathVariable UUID projectId,
            @Valid @RequestBody SubmitAnswersRequest request) {
        QAResponse response = qaService.submitAnswers(projectId, request);
        return ResponseEntity.ok(ApiResponse.<QAResponse>builder()
                .status(200).message("Answers submitted.").data(response).build());
    }

    /**
     * GET /api/v1/projects/{projectId}/pcsf
     * Returns the full PCSF object for the review screen.
     */
    @GetMapping("/{projectId}/pcsf")
    public ResponseEntity<ApiResponse<Pcsf>> getPcsf(@PathVariable String projectId) {
        Project project = findOrThrow(parseId(projectId));
        if (project.getPcsfJson() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Pcsf>builder()
                            .status(404).message("PCSF not yet initialised.").build());
        }
        try {
            Pcsf pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);
            return ResponseEntity.ok(ApiResponse.<Pcsf>builder()
                    .status(200).message("PCSF retrieved.").data(pcsf).build());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Pcsf>builder()
                            .status(500).message("Failed to parse PCSF: " + ex.getMessage()).build());
        }
    }

    /**
     * PATCH /api/v1/projects/{projectId}/pcsf/fields
     * Edit a single PCSF field during the review screen.
     */
    @PatchMapping("/{projectId}/pcsf/fields")
    public ResponseEntity<ApiResponse<Void>> patchField(
            @PathVariable UUID projectId,
            @Valid @RequestBody PatchFieldRequest request) {
        Project project = findOrThrow(projectId);
        if (project.getPcsfJson() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Void>builder().status(404).message("PCSF not found.").build());
        }
        try {
            Pcsf pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);
            fieldWriter.write(pcsf, request.getPath(), request.getValue());
            project.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            projectRepository.save(project);
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .status(200).message("Field updated.").build());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder().status(500).message("Patch failed: " + ex.getMessage()).build());
        }
    }

    /**
     * POST /api/v1/projects/{projectId}/pcsf/validate
     * Run all validation rules and lock the PCSF if they pass.
     */
    @PostMapping("/{projectId}/pcsf/validate")
    public ResponseEntity<ApiResponse<PcsfValidateResponse>> validatePcsf(
            @PathVariable String projectId) {
        PcsfValidateResponse result = validationService.validate(parseId(projectId));
        int status = result.isValid() ? 200 : 422;
        return ResponseEntity.status(status)
                .body(ApiResponse.<PcsfValidateResponse>builder()
                        .status(status)
                        .message(result.isValid() ? "PCSF validated and locked." : "Validation failed.")
                        .data(result).build());
    }

    /**
     * GET /api/v1/projects/{projectId}/pcsf/status
     * Lightweight polling endpoint. Angular polls this while pcsfStatus == INFERRING.
     */
    @GetMapping("/{projectId}/pcsf/status")
    public ResponseEntity<ApiResponse<PcsfStatusResponse>> getPcsfStatus(
            @PathVariable String projectId) {
        Project project = findOrThrow(parseId(projectId));
        double score = 0.0;
        if (project.getPcsfJson() != null) {
            try {
                Pcsf pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);
                if (pcsf.getValidation() != null) score = pcsf.getValidation().getCompletenessScore();
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.<PcsfStatusResponse>builder()
                .status(200).message("Status retrieved.")
                .data(PcsfStatusResponse.builder()
                        .pcsfStatus(project.getPcsfStatus().name())
                        .completenessScore(score)
                        .pendingQuestionsCount(project.getPendingQuestionsCount() != null
                                ? project.getPendingQuestionsCount() : 0)
                        .build())
                .build());
    }

    /**
     * GET /api/v1/projects/template
     * Download the Afriland project specification template DOCX.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(
                    "templates/Astyann_Project_Specification_Template.docx");
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Astyann_Project_Specification_Template.docx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project findOrThrow(UUID projectId) {
        return projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));
    }

    private ClarificationQuestionDto toDto(ClarificationQuestion q) {
        List<String> options = null;
        if (q.getOptionsJson() != null) {
            try {
                String[] arr = objectMapper.readValue(q.getOptionsJson(), String[].class);
                options = Arrays.asList(arr);
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
