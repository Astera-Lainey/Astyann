package afb.astyann.aiorchestrator.controller;

import afb.astyann.aiorchestrator.dto.*;
import afb.astyann.aiorchestrator.service.IAIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIController {

    private final IAIService aiService;

    /**
     * POST /api/v1/ai/generate
     * Execute a general AI content generation task.
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AIResponseDTO>> generateContent(
            @Valid @RequestBody AIRequestDTO dto) {
        AIResponseDTO data = aiService.generateContent(dto);
        return ResponseEntity.ok(ApiResponse.<AIResponseDTO>builder()
                .status(200)
                .message("Content generated successfully.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/ai/analyze
     * Analyze raw text input and extract structured requirements.
     */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AIResponseDTO>> analyzeRequirements(
            @Valid @RequestBody AnalyzeRequestDTO dto) {
        AIResponseDTO data = aiService.analyzeRequirements(dto);
        return ResponseEntity.ok(ApiResponse.<AIResponseDTO>builder()
                .status(200)
                .message("Requirements analyzed successfully.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/ai/validate
     * Validate content against provided rules.
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<AIResponseDTO>> validateContent(
            @Valid @RequestBody ValidateRequestDTO dto) {
        AIResponseDTO data = aiService.validateContent(dto);
        return ResponseEntity.ok(ApiResponse.<AIResponseDTO>builder()
                .status(200)
                .message("Content validated successfully.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/ai/context
     * Retrieve relevant context from the RAG service.
     */
    @PostMapping("/context")
    public ResponseEntity<ApiResponse<ContextResponseDTO>> retrieveContext(
            @Valid @RequestBody ContextRequestDTO dto) {
        ContextResponseDTO data = aiService.retrieveContext(dto);
        return ResponseEntity.ok(ApiResponse.<ContextResponseDTO>builder()
                .status(200)
                .message("Context retrieved successfully.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/ai/projects/{projectId}/analyze
     * Parse and analyze an uploaded project document (PDF or DOCX).
     * Called by ProjectService after a project document is uploaded.
     */
    @PostMapping(value = "/projects/{projectId}/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectAnalysisResponseDTO>> analyzeProjectDocument(
            @PathVariable UUID projectId,
            @RequestPart("document") MultipartFile document) {
        ProjectAnalysisResponseDTO data = aiService.analyzeProjectDocument(projectId, document);
        return ResponseEntity.ok(ApiResponse.<ProjectAnalysisResponseDTO>builder()
                .status(200)
                .message(data.isSufficient()
                        ? "Document analysis complete. Information is sufficient."
                        : "Document analysis complete. Additional information required.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/ai/projects/{projectId}/merge
     * Merge original document context with guided question answers.
     * Called by ProjectService after the user submits answers to guided questions.
     */
    @PostMapping("/projects/{projectId}/merge")
    public ResponseEntity<ApiResponse<ProjectAnalysisResponseDTO>> mergeDocumentAndAnswers(
            @PathVariable UUID projectId,
            @Valid @RequestBody MergeRequestDTO dto) {
        dto.setProjectId(projectId);
        ProjectAnalysisResponseDTO data = aiService.mergeDocumentAndAnswers(dto);
        return ResponseEntity.ok(ApiResponse.<ProjectAnalysisResponseDTO>builder()
                .status(200)
                .message("Project context merged successfully.")
                .data(data)
                .build());
    }
}
