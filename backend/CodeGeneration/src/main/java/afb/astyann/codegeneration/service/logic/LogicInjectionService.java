package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.client.AiOrchestratorClient;
import afb.astyann.codegeneration.client.RagClient;
import afb.astyann.codegeneration.domain.logic.AiModuleResponse;
import afb.astyann.codegeneration.domain.logic.StubMethod;
import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The AI logic-injection pass. Runs after backend template rendering and before ZIP packaging:
 * for each module, ships the current stubs + PCSF business context + RAG snippets to the AI
 * Orchestrator, asks for the fully-implemented module, and patches the generated files in place.
 *
 * <p>Modules are independent, so they are implemented in parallel on {@code logicExecutor}.
 * Failure of a single module is not fatal — the stub source is left in place and generation
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
    private final AdditionalFilePathResolver additionalFilePathResolver;
    private final AiJsonExtractor aiJsonExtractor;
    private final Executor logicExecutor;

    // Tolerant reader for model output. LLMs frequently emit JSON with unescaped control
    // characters (raw newlines/tabs inside string values), trailing commas, or odd escapes —
    // all of which the strict shared ObjectMapper rejects. This one accepts them.
    private final ObjectMapper lenientJson = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public LogicInjectionService(AiOrchestratorClient aiClient,
                                 RagClient ragClient,
                                 FilePatcher filePatcher,
                                 PromptBuilder promptBuilder,
                                 AdditionalFilePathResolver additionalFilePathResolver,
                                 AiJsonExtractor aiJsonExtractor,
                                 @Qualifier("logicExecutor") Executor logicExecutor) {
        this.aiClient = aiClient;
        this.ragClient = ragClient;
        this.filePatcher = filePatcher;
        this.promptBuilder = promptBuilder;
        this.additionalFilePathResolver = additionalFilePathResolver;
        this.aiJsonExtractor = aiJsonExtractor;
        this.logicExecutor = logicExecutor;
    }

    @Value("${codegen.ai.logic-injection.enabled:true}")
    private boolean enabled;

    @Value("${codegen.ai.model:gpt-oss:120b-cloud}")
    private String model;

    @Value("${codegen.ai.rag.top-k:5}")
    private int ragTopK;

    @Value("${codegen.ai.logic-injection.max-attempts:2}")
    private int maxAiAttempts;

    /**
     * Outcome of a whole-layer injection pass, persisted onto {@code GeneratedCode} so callers
     * and {@code validate()} can tell a logic-complete backend from a still-stubbed one.
     *
     * @param modulesTotal         modules the projection asked us to implement
     * @param modulesPatched       modules where at least one file was written
     * @param stubMethodsRemaining blocking completeness issues under {@code src/main/java}:
     *                             remaining stub methods PLUS files that fail to parse
     *                             ({@code 0} == verifiably complete)
     * @param stubDetails          human-readable labels for each blocking issue (stub
     *                             "Class.method" entries and "unparseable: File.java" entries)
     */
    public record InjectionResult(int modulesTotal, int modulesPatched,
                                  int stubMethodsRemaining, List<String> stubDetails) {}

    /**
     * Walks every module in the projection (in parallel) and delegates its implementation to the
     * AI. Errors are logged and swallowed so a flaky AI or RAG endpoint cannot break code
     * generation. The remaining-stub count is computed authoritatively by re-scanning the tree
     * after the pass, so it stays correct even when individual modules failed.
     */
    public InjectionResult inject(UUID projectId,
                                  Path backendRoot,
                                  BackendProjection projection,
                                  Pcsf pcsf) {
        String packagePath = projection.getProjectInfo().getPackagePath();
        Path srcMainJava = backendRoot.resolve("src/main/java");
        Path javaRoot = srcMainJava.resolve(packagePath);
        String packageHint = packagePath == null ? null : packagePath.replace('/', '.');
        int total = projection.getModules().size();

        if (!enabled) {
            log.info("Logic injection disabled — leaving stubs in place for project {}.", projectId);
            return toResult(total, 0, filePatcher.scanTree(javaRoot));
        }

        List<CompletableFuture<Boolean>> futures = projection.getModules().stream()
                .map(module -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return injectOneModule(projectId, javaRoot, srcMainJava, packageHint,
                                module, projection, pcsf);
                    } catch (Exception ex) {
                        log.warn("Logic injection failed for module {} (entity {}): {}",
                                module.getServiceName(), module.getEntityClassName(), ex.getMessage(), ex);
                        return false;
                    }
                }, logicExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        int patched = (int) futures.stream().filter(f -> Boolean.TRUE.equals(f.join())).count();
        FilePatcher.TreeScan scan = filePatcher.scanTree(javaRoot);

        log.info("Logic injection patched {}/{} modules for project {} ({} stub(s), {} unparseable file(s) remain).",
                patched, total, projectId, scan.stubs().size(), scan.unparseable().size());
        if (!scan.unparseable().isEmpty()) {
            log.warn("[project {}] {} generated file(s) did not parse and will block completion: {}",
                    projectId, scan.unparseable().size(),
                    scan.unparseable().stream().map(p -> p.getFileName().toString()).toList());
        }
        return toResult(total, patched, scan);
    }

    /**
     * Calls the AI Orchestrator for a module implementation, retrying up to
     * {@code codegen.ai.logic-injection.max-attempts} times when the reply is empty or cannot be
     * parsed. Returns {@code null} if every attempt fails (module keeps its stubs).
     */
    private AiModuleResponse requestModuleImplementation(BackendModule module, String userPrompt) {
        int attempts = Math.max(1, maxAiAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String content;
            try {
                var response = aiClient.infer(new AiOrchestratorClient.InferenceRequest(
                        model, promptBuilder.systemPrompt(), userPrompt));
                content = response == null ? null : response.content();
            } catch (Exception ex) {
                log.warn("AI Orchestrator call failed for module {} on attempt {}/{}: {}",
                        module.getServiceName(), attempt, attempts, ex.getMessage());
                continue;
            }
            if (content == null || content.isBlank()) {
                log.warn("AI Orchestrator returned empty content for module {} (attempt {}/{})",
                        module.getServiceName(), attempt, attempts);
                continue;
            }
            AiModuleResponse parsed = parseAiResponse(content);
            if (parsed != null) return parsed;
            // Log head AND tail: a tail that ends mid-string (no closing brace) means the reply was
            // truncated at the model's output-token limit — raise ai.ollama.num-predict. A malformed
            // tail that ends with "}" points at a JSON-shape issue instead.
            int len = content.length();
            log.warn("Could not parse AI response for module {} (attempt {}/{}, {} chars).\n  head: {}\n  tail: {}",
                    module.getServiceName(), attempt, attempts, len,
                    content.substring(0, Math.min(200, len)),
                    content.substring(Math.max(0, len - 200)));
        }
        return null;
    }

    /** Folds stubs + unparseable files into a single blocking count for the InjectionResult. */
    private InjectionResult toResult(int total, int patched, FilePatcher.TreeScan scan) {
        List<String> details = new ArrayList<>(stubLabels(scan.stubs()));
        for (Path p : scan.unparseable()) details.add("unparseable: " + p.getFileName());
        return new InjectionResult(total, patched, scan.blockingCount(), details);
    }

    private static List<String> stubLabels(List<StubMethod> stubs) {
        List<String> out = new ArrayList<>(stubs.size());
        for (StubMethod s : stubs) out.add(s.className() + "." + s.methodName());
        return out;
    }

    private boolean injectOneModule(UUID projectId,
                                    Path javaRoot,
                                    Path srcMainJava,
                                    String packageHint,
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

        // Ask the AI to implement the module, retrying on an empty or unparseable reply — a single
        // blank response (seen from some models) otherwise leaves the whole module stubbed for good.
        AiModuleResponse aiResponse = requestModuleImplementation(module, userPrompt);
        if (aiResponse == null) {
            log.warn("Giving up on module {} after {} attempt(s) — leaving stubs in place.",
                    module.getServiceName(), Math.max(1, maxAiAttempts));
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
                Path target = additionalFilePathResolver.resolve(srcMainJava, relative, packageHint).orElse(null);
                if (target == null) continue; // blank or escapes the tree (already logged)
                if (Files.exists(target)) {
                    log.debug("Skipping additional file {} — already exists.", relative);
                    continue;
                }
                try {
                    if (filePatcher.replaceEntireFile(target, source)) {
                        newFiles++;
                        anyWritten = true;
                        log.info("[{}] wrote additional file {}", module.getServiceName(),
                                srcMainJava.relativize(target));
                    }
                } catch (Exception ex) {
                    log.warn("Could not write additional file {}: {}", relative, ex.getMessage());
                }
            }
        }

        // ── Post-injection sanity check: warn if any stubs are still present ──
        List<StubMethod> remaining = filePatcher.findStubMethods(serviceImplFile);
        if (!remaining.isEmpty()) {
            log.warn("[{}] {} stub method(s) still remain after AI pass: {}",
                    module.getServiceName(), remaining.size(),
                    remaining.stream().map(StubMethod::methodName).toList());
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
            return lenientJson.readValue(trimmed, AiModuleResponse.class);
        } catch (Exception first) {
            Matcher m = JSON_ENVELOPE.matcher(trimmed);
            if (m.find()) {
                try {
                    return lenientJson.readValue(m.group(), AiModuleResponse.class);
                } catch (Exception ignored) {
                    // fall through to field-level salvage
                }
            }
            // Structural parse failed — almost always a mis-escaped quote or backslash buried in
            // the embedded Java source. Recover the fields individually instead of discarding a
            // response that is otherwise usable.
            log.debug("Structural JSON parse failed ({}), attempting field-level salvage.",
                    first.getMessage());
            return salvageModuleResponse(trimmed);
        }
    }

    /**
     * Field-level recovery for a reply whose overall JSON is malformed. Returns {@code null} only
     * when not even {@code serviceImpl} can be read — otherwise the recovered files are returned
     * and each is still validated by {@code FilePatcher} before anything is written.
     */
    private AiModuleResponse salvageModuleResponse(String raw) {
        String serviceImpl = aiJsonExtractor.stringField(raw, "serviceImpl").orElse(null);
        if (serviceImpl == null) return null;
        AiModuleResponse recovered = AiModuleResponse.builder()
                .serviceImpl(serviceImpl)
                .repository(aiJsonExtractor.stringField(raw, "repository").orElse(null))
                .controller(aiJsonExtractor.stringField(raw, "controller").orElse(null))
                .additionalFiles(aiJsonExtractor.stringMapField(raw, "additionalFiles"))
                .notes(aiJsonExtractor.stringField(raw, "notes").orElse(null))
                .build();
        log.info("Salvaged malformed AI JSON: serviceImpl={} chars, repository={}, controller={}, additionalFiles={}",
                serviceImpl.length(),
                recovered.getRepository() != null, recovered.getController() != null,
                recovered.getAdditionalFiles().size());
        return recovered;
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
