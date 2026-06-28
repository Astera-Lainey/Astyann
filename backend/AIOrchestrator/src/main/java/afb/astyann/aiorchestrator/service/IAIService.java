package afb.astyann.aiorchestrator.service;

import afb.astyann.aiorchestrator.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IAIService {

    /** Execute a general AI generation task with context retrieval. */
    AIResponseDTO generateContent(AIRequestDTO request);

    /** Analyze raw text input and extract structured requirements. */
    AIResponseDTO analyzeRequirements(AnalyzeRequestDTO request);

    /** Validate content against provided rules. */
    AIResponseDTO validateContent(ValidateRequestDTO request);

    /** Retrieve relevant context from the RAG service. */
    ContextResponseDTO retrieveContext(ContextRequestDTO request);

    /** Parse a project document and evaluate whether information is sufficient. */
    ProjectAnalysisResponseDTO analyzeProjectDocument(UUID projectId, MultipartFile document);

    /** Merge original document context with guided question answers into a complete project context. */
    ProjectAnalysisResponseDTO mergeDocumentAndAnswers(MergeRequestDTO request);

    /** Direct Ollama inference with explicit model selection. Returns raw content string. */
    String infer(String model, String systemPrompt, String userPrompt);
}
