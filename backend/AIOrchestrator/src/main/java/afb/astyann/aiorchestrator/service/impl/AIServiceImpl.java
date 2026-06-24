package afb.astyann.aiorchestrator.service.impl;

import afb.astyann.aiorchestrator.client.MetaModelServiceClient;
import afb.astyann.aiorchestrator.client.RAGServiceClient;
import afb.astyann.aiorchestrator.domain.AIRequest;
import afb.astyann.aiorchestrator.domain.AIResponse;
import afb.astyann.aiorchestrator.domain.AITaskType;
import afb.astyann.aiorchestrator.dto.*;
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
import java.util.Collections;
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

    private final RAGServiceClient        ragClient;
    private final MetaModelServiceClient  metaModelClient;
    private final AIProviderRouter        providerRouter;
    private final DocumentParserService   documentParserService;
    private final ObjectMapper            objectMapper;

    // ── Generate Content ──────────────────────────────────────────────────────

    @Override
    public AIResponseDTO generateContent(AIRequestDTO request) {
        log.info("generateContent projectId={} taskType={}", request.getProjectId(), request.getTaskType());

        String context  = fetchContext(request.getProjectId(), request.getPrompt());
        String template = fetchTemplate(request.getTaskType());
        String fullPrompt = buildPrompt(template, request.getPrompt(), context);

        AIResponse response = route(request.getProjectId(), request.getTaskType(), fullPrompt,
                ProviderConfig.builder().build());
        return toDTO(response);
    }

    // ── Analyze Requirements ──────────────────────────────────────────────────

    @Override
    public AIResponseDTO analyzeRequirements(AnalyzeRequestDTO request) {
        log.info("analyzeRequirements projectId={}", request.getProjectId());
        String prompt = "Analyze the following project information and extract structured software requirements:\n\n"
                + request.getRawInput();
        AIResponse response = route(request.getProjectId(), AITaskType.ANALYZE_REQUIREMENTS, prompt,
                ProviderConfig.builder().build());
        return toDTO(response);
    }

    // ── Validate Content ─────────────────────────────────────────────────────

    @Override
    public AIResponseDTO validateContent(ValidateRequestDTO request) {
        log.info("validateContent projectId={}", request.getProjectId());
        String rules  = request.getRules() != null ? request.getRules() : "standard software quality rules";
        String prompt = "Validate the following content against these rules: " + rules
                + "\n\nContent to validate:\n" + request.getContent();
        AIResponse response = route(request.getProjectId(), AITaskType.VALIDATE_CONTENT, prompt,
                ProviderConfig.builder().build());
        return toDTO(response);
    }

    // ── Retrieve Context ──────────────────────────────────────────────────────

    @Override
    public ContextResponseDTO retrieveContext(ContextRequestDTO request) {
        log.info("retrieveContext projectId={} query={}", request.getProjectId(), request.getQuery());
        String context = safeCall("RAG context",
                () -> ragClient.retrieveContext(request.getProjectId(), request.getQuery(), request.getTopK()),
                "");
        List<String> sources = safeCall("RAG sources",
                () -> ragClient.getSources(request.getProjectId(), request.getQuery()),
                Collections.emptyList());
        return ContextResponseDTO.builder().context(context).sources(sources).build();
    }

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

        return parseAnalysisResponse(projectId, response.getContent());
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

        return parseAnalysisResponse(request.getProjectId(), response.getContent());
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

    private AIResponseDTO toDTO(AIResponse response) {
        return AIResponseDTO.builder()
                .responseId(response.getResponseId())
                .content(response.getContent())
                .providerName(response.getProviderName())
                .tokensUsed(response.getTokensUsed())
                .build();
    }

    private String fetchContext(UUID projectId, String query) {
        return safeCall("RAG context", () -> ragClient.retrieveContext(projectId, query, 5), "");
    }

    private String fetchTemplate(AITaskType taskType) {
        return safeCall("MetaModel template", () -> metaModelClient.getPromptTemplate(taskType), "");
    }

    private String buildPrompt(String template, String userPrompt, String context) {
        if (template == null || template.isBlank()) {
            return context.isBlank() ? userPrompt : userPrompt + "\n\nContext:\n" + context;
        }
        return template.replace("{prompt}", userPrompt).replace("{context}", context);
    }

    private ProjectAnalysisResponseDTO parseAnalysisResponse(UUID projectId, String llmContent) {
        try {
            // Strip markdown code fences if the LLM wraps JSON in them
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
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T safeCall(String name, java.util.concurrent.Callable<T> call, T fallback) {
        try {
            return call.call();
        } catch (Exception ex) {
            log.warn("{} unavailable: {}", name, ex.getMessage());
            return fallback;
        }
    }
}
