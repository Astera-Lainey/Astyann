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

    @PostMapping(value = "/projects/{projectId}/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectAnalysisResponseDTO> analyzeProjectDocument(
            @PathVariable UUID projectId,
            @RequestPart("document") MultipartFile document) {
        return ResponseEntity.ok(aiService.analyzeProjectDocument(projectId, document));
    }

    @PostMapping("/projects/{projectId}/merge")
    public ResponseEntity<ProjectAnalysisResponseDTO> mergeDocumentAndAnswers(
            @PathVariable UUID projectId,
            @Valid @RequestBody MergeRequestDTO dto) {
        dto.setProjectId(projectId);
        return ResponseEntity.ok(aiService.mergeDocumentAndAnswers(dto));
    }

    /**
     * POST /api/v1/ai/infer
     * Direct model inference with explicit model selection. Used by PCSF pipeline.
     * Returns raw model output wrapped in InferenceResponseDTO.
     */
    @PostMapping("/infer")
    public ResponseEntity<InferenceResponseDTO> infer(@Valid @RequestBody InferenceRequestDTO dto) {
        String content = aiService.infer(dto.getModel(), dto.getSystemPrompt(), dto.getUserPrompt());
        return ResponseEntity.ok(new InferenceResponseDTO(dto.getModel(), content));
    }
}
