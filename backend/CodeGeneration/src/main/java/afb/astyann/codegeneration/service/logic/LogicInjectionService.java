package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.client.AiOrchestratorClient;
import afb.astyann.codegeneration.client.RagClient;
import afb.astyann.codegeneration.domain.logic.AiModuleResponse;
import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The AI logic-injection pass. Runs after backend template rendering and before ZIP packaging:
 * for each module, ships the current stubs + PCSF business context + RAG snippets to the AI
 * Orchestrator, asks for the fully-implemented module, and patches the generated files in place.
 *
 * <p>Failure of a single module is not fatal — the stub source is left in place and generation
 * continues. This mirrors the "best-effort snapshot" pattern used elsewhere in the codebase.
 */
@Service
@Slf4j
public class LogicInjectionService {

    private static final Pattern JSON_ENVELOPE = Pattern.compile("\\{[\\s\\S]*}");

    private final AiOrchestratorClient aiClient;
    private final RagClient ragClient;
    private final FilePatcher filePatcher;
    private final PromptBuilder promptBuilder;
    // Self-contained ObjectMapper — this service doesn't need the shared Spring one and it
    // avoids a required-dependency on spring-boot-starter-web in downstream services.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogicInjectionService(AiOrchestratorClient aiClient,
                                 RagClient ragClient,
                                 FilePatcher filePatcher,
                                 PromptBuilder promptBuilder) {
        this.aiClient = aiClient;
        this.ragClient = ragClient;
        this.filePatcher = filePatcher;
        this.promptBuilder = promptBuilder;
    }

    @Value("${codegen.ai.logic-injection.enabled:true}")
    private boolean enabled;

    @Value("${codegen.ai.model:claude-sonnet-4-5}")
    private String model;

    @Value("${codegen.ai.rag.top-k:5}")
    private int ragTopK;

    /**
     * Walks every module in the projection and delegates its implementation to the AI. Errors
     * are logged and swallowed so a flaky AI or RAG endpoint cannot break code generation.
     *
     * @return count of modules successfully patched.
     */
    public int inject(UUID projectId,
                      Path backendRoot,
                      BackendProjection projection,
                      Pcsf pcsf) {
        if (!enabled) {
            log.info("Logic injection disabled — leaving stubs in place for project {}.", projectId);
            return 0;
        }
        int patched = 0;
        String packagePath = projection.getProjectInfo().getPackagePath();
        Path javaRoot = backendRoot.resolve("src/main/java").resolve(packagePath);

        for (BackendModule module : projection.getModules()) {
            try {
                if (injectOneModule(projectId, javaRoot, module, projection, pcsf)) patched++;
            } catch (Exception ex) {
                log.warn("Logic injection failed for module {} (entity {}): {}",
                        module.getServiceName(), module.getEntityClassName(), ex.getMessage(), ex);
            }
        }
        log.info("Logic injection patched {}/{} modules for project {}.",
                patched, projection.getModules().size(), projectId);
        return patched;
    }

    private boolean injectOneModule(UUID projectId,
                                    Path javaRoot,
                                    BackendModule module,
                                    BackendProjection projection,
                                    Pcsf pcsf) throws IOException {
        Path serviceImplFile = javaRoot.resolve("service/impl").resolve(module.getServiceImplName() + ".java");
        Path repositoryFile  = javaRoot.resolve("repository").resolve(module.getEntityClassName() + "Repository.java");
        Path controllerFile  = javaRoot.resolve("controller").resolve(module.getControllerName() + ".java");
        Path entityFile      = javaRoot.resolve("entity").resolve(module.getEntityClassName() + ".java");

        if (!Files.exists(serviceImplFile) || !Files.exists(entityFile)) {
            log.debug("Skipping module {}: expected source files not present.", module.getServiceName());
            return false;
        }

        String serviceImplSource = filePatcher.read(serviceImplFile);
        String repositorySource  = filePatcher.read(repositoryFile);
        String controllerSource  = filePatcher.read(controllerFile);
        String entitySource      = filePatcher.read(entityFile);
        List<String> repoSignatures = filePatcher.methodSignatures(repositoryFile);

        // Give the AI the FULL catalog of already-declared types so it doesn't hallucinate.
        Map<String, String> allEntitySources = loadJavaSources(javaRoot.resolve("entity"));
        Map<String, String> allRepositorySources = loadJavaSources(javaRoot.resolve("repository"));
        List<String> allDtoClassNames = new ArrayList<>(loadJavaSources(javaRoot.resolve("dto")).keySet());

        PcsfModule pcsfModule = findMatchingPcsfModule(pcsf, module);
        PcsfEntity primaryEntity = findMatchingPcsfEntity(pcsf, module);
        List<PcsfBusinessRule> rules = filterBusinessRules(pcsf, module, pcsfModule);

        String ragQuery = "Business logic and implementation for module "
                + safeName(pcsfModule) + " on entity " + module.getEntityClassName();
        String ragContext = fetchRagContextSafely(projectId, ragQuery);

        String userPrompt = promptBuilder.userPrompt(module, pcsfModule, primaryEntity, rules,
                serviceImplSource, repositorySource, controllerSource, entitySource,
                repoSignatures, allEntitySources, allRepositorySources, allDtoClassNames, ragContext);

        String content;
        try {
            var response = aiClient.infer(new AiOrchestratorClient.InferenceRequest(
                    model, promptBuilder.systemPrompt(), userPrompt));
            content = response == null ? null : response.content();
        } catch (FeignException ex) {
            log.warn("AI Orchestrator refused module {} (HTTP {}): {}",
                    module.getServiceName(), ex.status(), ex.getMessage());
            return false;
        }
        if (content == null || content.isBlank()) {
            log.warn("AI Orchestrator returned empty content for module {}", module.getServiceName());
            return false;
        }

        AiModuleResponse aiResponse = parseAiResponse(content);
        if (aiResponse == null) {
            log.warn("Could not parse AI response for module {} (first 200 chars): {}",
                    module.getServiceName(),
                    content.substring(0, Math.min(200, content.length())));
            return false;
        }

        boolean anyWritten = false;
        if (aiResponse.getServiceImpl() != null && !aiResponse.getServiceImpl().isBlank()) {
            if (filePatcher.replaceEntireFile(serviceImplFile, aiResponse.getServiceImpl())) {
                anyWritten = true;
                log.debug("Patched {}", serviceImplFile.getFileName());
            }
        }
        if (aiResponse.getRepository() != null && !aiResponse.getRepository().isBlank()) {
            if (filePatcher.replaceEntireFile(repositoryFile, aiResponse.getRepository())) {
                anyWritten = true;
                log.debug("Patched {}", repositoryFile.getFileName());
            }
        }
        if (aiResponse.getController() != null && !aiResponse.getController().isBlank()) {
            if (filePatcher.replaceEntireFile(controllerFile, aiResponse.getController())) {
                anyWritten = true;
                log.debug("Patched {}", controllerFile.getFileName());
            }
        }

        // ── Write additional files the AI declared (enums, exceptions, helpers) ──
        int newFiles = 0;
        if (aiResponse.getAdditionalFiles() != null) {
            for (var entry : aiResponse.getAdditionalFiles().entrySet()) {
                String relative = entry.getKey();
                String source = entry.getValue();
                if (relative == null || relative.isBlank() || source == null || source.isBlank()) continue;
                Path target = javaRoot.resolve(relative).normalize();
                if (!target.startsWith(javaRoot)) {
                    log.warn("Refusing suspicious additional-file path from AI: {}", relative);
                    continue;
                }
                if (Files.exists(target)) {
                    log.debug("Skipping additional file {} — already exists.", relative);
                    continue;
                }
                try {
                    if (filePatcher.replaceEntireFile(target, source)) {
                        newFiles++;
                        anyWritten = true;
                        log.info("[{}] wrote additional file {}", module.getServiceName(), relative);
                    }
                } catch (Exception ex) {
                    log.warn("Could not write additional file {}: {}", relative, ex.getMessage());
                }
            }
        }

        // ── Post-injection sanity check: warn if any stubs are still present ──
        List<afb.astyann.codegeneration.domain.logic.StubMethod> remaining =
                filePatcher.findStubMethods(serviceImplFile);
        if (!remaining.isEmpty()) {
            log.warn("[{}] {} stub method(s) still remain after AI pass: {}",
                    module.getServiceName(), remaining.size(),
                    remaining.stream().map(afb.astyann.codegeneration.domain.logic.StubMethod::methodName).toList());
        }

        if (aiResponse.getNotes() != null && !aiResponse.getNotes().isBlank()) {
            log.info("[{}] {} (additional files: {})",
                    module.getServiceName(), aiResponse.getNotes(), newFiles);
        }
        return anyWritten;
    }

    /** Reads every {@code *.java} file directly under {@code dir} into a
     *  {@code ClassName -> source} map. Missing directories return empty. */
    private Map<String, String> loadJavaSources(Path dir) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.exists(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".java"))
                    .forEach(f -> {
                        try {
                            String name = f.getFileName().toString().replace(".java", "");
                            out.put(name, filePatcher.read(f));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ex) {
            log.debug("Could not list {}: {}", dir, ex.getMessage());
        }
        return out;
    }

    // ── AI response parsing ────────────────────────────────────────────────

    /**
     * Accepts either a raw JSON payload, JSON wrapped in {@code ```json ... ```}, or JSON
     * embedded within other text. Extracts the first balanced-looking {@code { ... }} region
     * and delegates to Jackson.
     */
    private AiModuleResponse parseAiResponse(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        try {
            return objectMapper.readValue(trimmed, AiModuleResponse.class);
        } catch (Exception first) {
            Matcher m = JSON_ENVELOPE.matcher(trimmed);
            if (m.find()) {
                try {
                    return objectMapper.readValue(m.group(), AiModuleResponse.class);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    // ── PCSF lookups ───────────────────────────────────────────────────────

    private PcsfModule findMatchingPcsfModule(Pcsf pcsf, BackendModule module) {
        if (pcsf.getModules() == null) return null;
        String needle = module.getRequestMapping();
        String kebab = needle == null ? "" : needle.substring(needle.lastIndexOf('/') + 1).toLowerCase();
        return pcsf.getModules().stream()
                .filter(m -> {
                    String name = fvStr(m.getName());
                    return name != null && name.toLowerCase().replaceAll("[^a-z0-9]+", "-").contains(kebab);
                })
                .findFirst().orElse(null);
    }

    private PcsfEntity findMatchingPcsfEntity(Pcsf pcsf, BackendModule module) {
        if (pcsf.getEntities() == null) return null;
        return pcsf.getEntities().stream()
                .filter(e -> module.getEntityClassName().equals(fvStr(e.getName())))
                .findFirst().orElse(null);
    }

    private List<PcsfBusinessRule> filterBusinessRules(Pcsf pcsf, BackendModule module, PcsfModule pcsfModule) {
        if (pcsf.getBusinessRules() == null) return List.of();
        String moduleId = pcsfModule != null ? pcsfModule.getId() : null;
        List<PcsfBusinessRule> out = new ArrayList<>();
        for (PcsfBusinessRule r : pcsf.getBusinessRules()) {
            boolean moduleMatch = moduleId != null && moduleId.equals(r.getModuleId());
            boolean entityMatch = r.getAffectedEntityId() != null
                    && module.getEntityClassName().equalsIgnoreCase(r.getAffectedEntityId());
            if (moduleMatch || entityMatch) out.add(r);
        }
        return out;
    }

    // ── RAG ────────────────────────────────────────────────────────────────

    private String fetchRagContextSafely(UUID projectId, String query) {
        try {
            return ragClient.getContext(projectId, query, ragTopK);
        } catch (Exception ex) {
            log.debug("RAG context lookup failed for project {}: {}. Continuing without it.",
                    projectId, ex.getMessage());
            return "";
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String safeName(PcsfModule m) {
        return m == null ? "unknown" : fvStr(m.getName());
    }

    private static String fvStr(FieldValue<?> f) {
        if (f == null || f.getValue() == null) return null;
        return String.valueOf(f.getValue());
    }
}
