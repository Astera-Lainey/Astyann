package afb.astyann.aiorchestrator.service;

import afb.astyann.aiorchestrator.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IAIService {

    /** Parse a project document and evaluate whether information is sufficient. */
    ProjectAnalysisResponseDTO analyzeProjectDocument(UUID projectId, MultipartFile document);

    /** Merge original document context with guided question answers into a complete project context. */
    ProjectAnalysisResponseDTO mergeDocumentAndAnswers(MergeRequestDTO request);

    /** Direct Ollama inference with explicit model selection. Returns raw content string. */
    String infer(String model, String systemPrompt, String userPrompt);
}
