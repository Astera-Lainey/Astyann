package afb.astyann.aiorchestrator.service.impl;

import afb.astyann.aiorchestrator.domain.AIRequest;
import afb.astyann.aiorchestrator.domain.AIResponse;
import afb.astyann.aiorchestrator.domain.AITaskType;
import afb.astyann.aiorchestrator.dto.*;
import afb.astyann.aiorchestrator.provider.OllamaProvider;
import afb.astyann.aiorchestrator.provider.ProviderConfig;
import afb.astyann.aiorchestrator.service.AIProviderRouter;
import afb.astyann.aiorchestrator.service.DocumentParserService;
import afb.astyann.aiorchestrator.service.IAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIServiceImpl implements IAIService {

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            You are an expert software project analyst. Analyze project documentation to extract \
            requirements and evaluate completeness for software development.
            Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
            """;

    private static final String MERGE_SYSTEM_PROMPT = """
            You are an expert software project analyst. Combine a project's original document context \
            with the user's answers to guided questions into a complete project description.
            Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
            """;

    private final AIProviderRouter        providerRouter;
    private final OllamaProvider          ollamaProvider;
    private final DocumentParserService   documentParserService;
    private final ObjectMapper            objectMapper;

    // ── Analyze Project Document ──────────────────────────────────────────────

    @Override
    public ProjectAnalysisResponseDTO analyzeProjectDocument(UUID projectId, MultipartFile document) {
        log.info("analyzeProjectDocument projectId={} filename={}", projectId,
                document.getOriginalFilename());

        String documentText = documentParserService.extractText(document);
        log.debug("Extracted {} characters from document", documentText.length());

        String prompt = """
                Analyze the following project document. Determine if it contains sufficient information \
                to begin software development.

                Rules:
                - "sufficient" = true when the document clearly describes project goals, key features, and target users
                - "extractedContext" = a concise summary (max 500 words) of what the document contains
                - "guidedQuestions" = empty array if sufficient; otherwise 3–5 specific questions to gather missing info

                Respond with ONLY this JSON structure (no markdown, no extra text):
                {
                  "sufficient": true,
                  "extractedContext": "...",
                  "guidedQuestions": []
                }

                Document content:
                """ + documentText;

        AIResponse response = route(projectId, AITaskType.ANALYZE_REQUIREMENTS, prompt,
                ProviderConfig.builder().systemPrompt(ANALYSIS_SYSTEM_PROMPT).maxTokens(2048).build());

        return parseAnalysisResponse(projectId, response.getContent(), documentText);
    }

    // ── Merge Document + Answers ──────────────────────────────────────────────

    @Override
    public ProjectAnalysisResponseDTO mergeDocumentAndAnswers(MergeRequestDTO request) {
        log.info("mergeDocumentAndAnswers projectId={}", request.getProjectId());

        String formattedAnswers = request.getAnswers() == null ? "" :
                request.getAnswers().stream()
                        .map(a -> "Q: " + a.getQuestion() + "\nA: " + a.getAnswer())
                        .collect(Collectors.joining("\n\n"));

        String prompt = """
                Combine the original project context with the user's answers to create a complete \
                project description.

                Original context:
                """ + request.getDocumentContext() + """

                User answers to guided questions:
                """ + formattedAnswers + """

                Respond with ONLY this JSON structure (no markdown, no extra text):
                {
                  "sufficient": true,
                  "extractedContext": "complete project description here",
                  "guidedQuestions": []
                }
                """;

        AIResponse response = route(request.getProjectId(), AITaskType.ANALYZE_REQUIREMENTS, prompt,
                ProviderConfig.builder().systemPrompt(MERGE_SYSTEM_PROMPT).maxTokens(2048).build());

        return parseAnalysisResponse(request.getProjectId(), response.getContent(), null);
    }

    // ── Direct Inference ──────────────────────────────────────────────────────

    @Override
    public String infer(String model, String systemPrompt, String userPrompt) {
        log.debug("Direct inference model={}", model);
        ProviderConfig config = ProviderConfig.builder()
                .systemPrompt(systemPrompt)
                .maxTokens(16384)
                .modelOverride(model)
                .build();
        return ollamaProvider.complete(userPrompt, config);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AIResponse route(UUID projectId, AITaskType taskType, String prompt, ProviderConfig config) {
        AIRequest request = AIRequest.builder()
                .requestId(UUID.randomUUID())
                .projectId(projectId)
                .taskType(taskType)
                .prompt(prompt)
                .config(config)
                .build();
        return providerRouter.route(request);
    }

    private ProjectAnalysisResponseDTO parseAnalysisResponse(UUID projectId, String llmContent,
                                                               String documentText) {
        try {
            String json = llmContent
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);
            boolean sufficient = root.path("sufficient").asBoolean(false);
            String extractedContext = root.path("extractedContext").asText("");

            List<String> questions = new ArrayList<>();
            JsonNode questionsNode = root.path("guidedQuestions");
            if (questionsNode.isArray()) {
                questionsNode.forEach(q -> questions.add(q.asText()));
            }

            return ProjectAnalysisResponseDTO.builder()
                    .projectId(projectId)
                    .sufficient(sufficient)
                    .extractedContext(extractedContext)
                    .guidedQuestions(questions)
                    .documentText(documentText)
                    .build();

        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON response, treating as insufficient. content={}", llmContent);
            return ProjectAnalysisResponseDTO.builder()
                    .projectId(projectId)
                    .sufficient(false)
                    .extractedContext(llmContent)
                    .guidedQuestions(List.of(
                            "What are the main goals of this project?",
                            "Who are the target users?",
                            "What are the most important features?"))
                    .documentText(documentText)
                    .build();
        }
    }
}
