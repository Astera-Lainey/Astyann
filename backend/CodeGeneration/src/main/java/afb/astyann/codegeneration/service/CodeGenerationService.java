package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.client.DocumentServiceClient;
import afb.astyann.codegeneration.client.RequirementServiceClient;
import afb.astyann.codegeneration.client.VersionServiceClient;
import afb.astyann.codegeneration.domain.CodeLayer;
import afb.astyann.codegeneration.domain.CodeStatus;
import afb.astyann.codegeneration.domain.CodeVersionArchive;
import afb.astyann.codegeneration.domain.GeneratedCode;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import afb.astyann.codegeneration.domain.projection.FrontendEntity;
import afb.astyann.codegeneration.domain.projection.FrontendModule;
import afb.astyann.codegeneration.domain.projection.FrontendProjection;
import afb.astyann.codegeneration.domain.projection.InfraProjection;
import afb.astyann.codegeneration.dto.ApiResponse;
import afb.astyann.codegeneration.dto.ValidationCheckDTO;
import afb.astyann.codegeneration.dto.ValidationReportDTO;
import afb.astyann.codegeneration.exception.CodeNotFoundException;
import afb.astyann.codegeneration.exception.CodeVersionNotFoundException;
import afb.astyann.codegeneration.exception.DocumentsNotApprovedException;
import afb.astyann.codegeneration.exception.DownstreamServiceException;
import afb.astyann.codegeneration.exception.PcsfNotApprovedException;
import afb.astyann.codegeneration.client.AiOrchestratorClient;
import afb.astyann.codegeneration.repository.CodeVersionArchiveRepository;
import afb.astyann.codegeneration.repository.GeneratedCodeRepository;
import afb.astyann.codegeneration.service.logic.FilePatcher;
import afb.astyann.codegeneration.service.logic.LogicInjectionService;
import afb.astyann.codegeneration.service.logic.MavenRunner;
import afb.astyann.codegeneration.service.logic.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeGenerationService {

    private static final String DESIGN_SYSTEM_RESOURCE_PATTERN = "classpath*:/design-system/**";
    private static final String DESIGN_SYSTEM_ROOT_PREFIX = "design-system/";

    private final GeneratedCodeRepository repository;
    private final CodeVersionArchiveRepository archiveRepository;
    private final RequirementServiceClient requirementServiceClient;
    private final DocumentServiceClient documentServiceClient;
    private final VersionServiceClient versionServiceClient;
    private final AiOrchestratorClient aiOrchestratorClient;
    private final ProjectionBuilder projectionBuilder;
    private final FreeMarkerEngine freeMarkerEngine;
    private final MustacheEngine mustacheEngine;
    private final StorageService storageService;
    private final LogicInjectionService logicInjectionService;
    private final FilePatcher filePatcher;
    private final MavenRunner mavenRunner;
    private final PromptBuilder promptBuilder;

    @Qualifier("codeExecutor")
    private final Executor codeExecutor;

    @Value("${codegen.ai.model:claude-sonnet-4-5}")
    private String aiModel;

    @Value("${codegen.validate.compile.enabled:true}")
    private boolean compileValidationEnabled;

    @Value("${codegen.validate.compile.max-attempts:3}")
    private int compileMaxAttempts;

    // ── Generate ────────────────────────────────────────────────────────────────

    /**
     * Verifies the upstream gates, marks each selected layer GENERATING, and dispatches each
     * layer's template/ZIP pipeline to the code executor — returns immediately with the
     * placeholders in GENERATING (mirrors DiagramGenerationService). Poll GET /{projectId}
     * until no layer is left in GENERATING to see the outcome per layer.
     *
     * @param layers layers to generate; null / empty means every {@link CodeLayer}.
     */
    public List<GeneratedCode> generate(UUID projectId, List<CodeLayer> layers) {
        verifyPcsfApproved(projectId);
        verifyAllDocumentsApproved(projectId);

        List<CodeLayer> targetLayers = (layers == null || layers.isEmpty())
                ? List.of(CodeLayer.values())
                : layers.stream().distinct().toList();

        List<GeneratedCode> placeholders = new ArrayList<>();
        for (CodeLayer layer : targetLayers) {
            placeholders.add(startLayer(projectId, layer));
        }

        placeholders.forEach(p -> codeExecutor.execute(() -> {
            try {
                generateLayer(projectId, p.getCodeId(), p.getLayer());
            } catch (Exception ex) {
                log.error("Unexpected failure generating {} for project {}: {}",
                        p.getLayer(), projectId, ex.getMessage(), ex);
                markFailed(p.getCodeId(), ex.getMessage());
            }
        }));

        return placeholders;
    }

    private GeneratedCode startLayer(UUID projectId, CodeLayer layer) {
        GeneratedCode code = repository.findByProjectIdAndLayer(projectId, layer).orElseGet(GeneratedCode::new);
        code.setProjectId(projectId);
        code.setLayer(layer);
        code.setStatus(CodeStatus.GENERATING);
        code.setLastError(null);
        return repository.save(code);
    }

    private void generateLayer(UUID projectId, UUID codeId, CodeLayer layer) {
        Pcsf pcsf = fetchPcsf(projectId);

        Path workDir;
        try {
            workDir = Files.createTempDirectory("astyann-code-" + projectId + "-" + layer.name().toLowerCase());
        } catch (IOException ex) {
            markFailed(codeId, "Could not create working directory: " + ex.getMessage());
            return;
        }

        try {
            Path layerRoot = workDir.resolve(layer.name().toLowerCase());
            switch (layer) {
                case BACKEND -> {
                    BackendProjection projection = projectionBuilder.buildBackendProjection(pcsf);
                    renderBackend(projection, layerRoot);
                    // ── AI logic injection: fill in every stub method body per module. ──
                    // Runs against the freshly-rendered files in the working dir before the
                    // ZIP is packaged. Best-effort — failures leave stubs in place.
                    try {
                        logicInjectionService.inject(projectId, layerRoot, projection, pcsf);
                    } catch (Exception aiEx) {
                        log.warn("Logic injection pass failed for project {}: {}",
                                projectId, aiEx.getMessage(), aiEx);
                    }
                }
                case FRONTEND -> renderFrontend(projectionBuilder.buildFrontendProjection(pcsf), layerRoot);
                case INFRASTRUCTURE -> renderInfrastructure(projectionBuilder.buildInfraProjection(pcsf), layerRoot);
            }
            byte[] zip = zipDirectory(layerRoot);
            String path = storageService.saveZip(projectId, layer, zip);

            GeneratedCode code = repository.findById(codeId).orElseThrow(() -> new CodeNotFoundException(projectId));
            code.setStatus(CodeStatus.GENERATED);
            code.setCodePath(path);
            code.setDownloadUrl("/api/v1/code/" + projectId + "/download?layer=" + layer.name());
            code.setLastError(null);
            repository.save(code);
        } catch (Exception ex) {
            log.error("{} code generation failed for project {}: {}", layer, projectId, ex.getMessage(), ex);
            markFailed(codeId, ex.getMessage());
        } finally {
            deleteQuietly(workDir);
        }
    }

    // ── Backend rendering ────────────────────────────────────────────────────────

    private void renderBackend(BackendProjection projection, Path backendRoot) throws IOException {
        var info = projection.getProjectInfo();
        Path javaRoot = backendRoot.resolve("src/main/java").resolve(info.getPackagePath());
        Path resources = backendRoot.resolve("src/main/resources");
        String appClassName = toPascalCase(info.getArtifactId()) + "Application";

        Map<String, Object> base = new HashMap<>();
        base.put("project", info);
        base.put("entities", projection.getEntities());
        base.put("modules", projection.getModules());
        base.put("roles", projection.getRoles());
        base.put("appClassName", appClassName);

        for (BackendEntity entity : projection.getEntities()) {
            Map<String, Object> model = new HashMap<>(base);
            model.put("entity", entity);
            write(javaRoot.resolve("entity").resolve(entity.getClassName() + ".java"),
                    freeMarkerEngine.render("backend/Entity.java.ftl", model));
            write(javaRoot.resolve("repository").resolve(entity.getClassName() + "Repository.java"),
                    freeMarkerEngine.render("backend/Repository.java.ftl", model));
            write(javaRoot.resolve("dto").resolve("Create" + entity.getClassName() + "Dto.java"),
                    freeMarkerEngine.render("backend/CreateDto.java.ftl", model));
            write(javaRoot.resolve("dto").resolve(entity.getClassName() + "ResponseDto.java"),
                    freeMarkerEngine.render("backend/ResponseDto.java.ftl", model));
        }

        Map<String, BackendEntity> entityByClass = new HashMap<>();
        for (BackendEntity e : projection.getEntities()) entityByClass.put(e.getClassName(), e);
        for (BackendModule module : projection.getModules()) {
            Map<String, Object> model = new HashMap<>(base);
            model.put("module", module);
            BackendEntity moduleEntity = entityByClass.get(module.getEntityClassName());
            if (moduleEntity == null) {
                moduleEntity = BackendEntity.builder().className(module.getEntityClassName())
                        .instanceName(module.getEntityInstanceName()).build();
            }
            model.put("entity", moduleEntity);
            write(javaRoot.resolve("service").resolve(module.getServiceName() + ".java"),
                    freeMarkerEngine.render("backend/ServiceInterface.java.ftl", model));
            write(javaRoot.resolve("service/impl").resolve(module.getServiceImplName() + ".java"),
                    freeMarkerEngine.render("backend/ServiceImpl.java.ftl", model));
            write(javaRoot.resolve("controller").resolve(module.getControllerName() + ".java"),
                    freeMarkerEngine.render("backend/Controller.java.ftl", model));
        }

        write(javaRoot.resolve(appClassName + ".java"),
                freeMarkerEngine.render("backend/Application.java.ftl", base));
        write(javaRoot.resolve("security").resolve("SecurityConfig.java"),
                freeMarkerEngine.render("backend/SecurityConfig.java.ftl", base));
        write(javaRoot.resolve("security").resolve("JwtFilter.java"),
                freeMarkerEngine.render("backend/JwtFilter.java.ftl", base));
        write(resources.resolve("application.properties"),
                freeMarkerEngine.render("backend/ApplicationProperties.ftl", base));
        write(backendRoot.resolve("pom.xml"),
                freeMarkerEngine.render("backend/PomXml.ftl", base));
        write(backendRoot.resolve("Dockerfile"),
                freeMarkerEngine.render("backend/BackendDockerfile.ftl", base));
    }

    // ── Frontend rendering ───────────────────────────────────────────────────────

    private void renderFrontend(FrontendProjection projection, Path frontendRoot) throws IOException {
        var info = projection.getProjectInfo();
        Path appRoot = frontendRoot.resolve("src/app");
        Path modelsRoot = appRoot.resolve("core/models");
        Path servicesRoot = appRoot.resolve("core/services");
        Path featuresRoot = appRoot.resolve("features");
        Path layoutRoot = appRoot.resolve("layout");
        Path stylesRoot = frontendRoot.resolve("src/styles");
        Path envRoot = frontendRoot.resolve("src/environments");

        Map<String, Object> base = new HashMap<>();
        base.put("project", info);
        base.put("entities", projection.getEntities());
        base.put("modules", projection.getModules());
        base.put("navigation", projection.getNavigation());

        for (FrontendEntity entity : projection.getEntities()) {
            Map<String, Object> model = new HashMap<>(base);
            model.put("entity", entity);
            write(modelsRoot.resolve(entity.getFileName() + ".model.ts"),
                    mustacheEngine.render("frontend/model.ts.mustache", model));
        }

        Map<String, FrontendEntity> entityByClass = new HashMap<>();
        for (FrontendEntity e : projection.getEntities()) entityByClass.put(e.getClassName(), e);
        for (FrontendModule module : projection.getModules()) {
            Map<String, Object> model = new HashMap<>(base);
            model.put("module", module);
            FrontendEntity moduleEntity = entityByClass.get(module.getEntityClassName());
            model.put("entity", moduleEntity);
            Path featureDir = featuresRoot.resolve(module.getComponentPrefix());

            write(servicesRoot.resolve(module.getServiceFileName() + ".service.ts"),
                    mustacheEngine.render("frontend/service.ts.mustache", model));
            write(featureDir.resolve("list").resolve(module.getComponentPrefix() + "-list.component.ts"),
                    mustacheEngine.render("frontend/list.component.ts.mustache", model));
            write(featureDir.resolve("list").resolve(module.getComponentPrefix() + "-list.component.html"),
                    mustacheEngine.render("frontend/list.component.html.mustache", model));
            write(featureDir.resolve("form").resolve(module.getComponentPrefix() + "-form.component.ts"),
                    mustacheEngine.render("frontend/form.component.ts.mustache", model));
            write(featureDir.resolve("form").resolve(module.getComponentPrefix() + "-form.component.html"),
                    mustacheEngine.render("frontend/form.component.html.mustache", model));
        }

        write(appRoot.resolve("app.routes.ts"),
                mustacheEngine.render("frontend/app.routes.ts.mustache", base));
        write(appRoot.resolve("app.config.ts"),
                mustacheEngine.render("frontend/app.config.ts.mustache", base));
        write(appRoot.resolve("core/interceptors/jwt.interceptor.ts"),
                mustacheEngine.render("frontend/jwt.interceptor.ts.mustache", base));
        write(appRoot.resolve("core/guards/auth.guard.ts"),
                mustacheEngine.render("frontend/auth.guard.ts.mustache", base));
        write(envRoot.resolve("environment.ts"),
                mustacheEngine.render("frontend/environment.ts.mustache", base));
        write(frontendRoot.resolve("package.json"),
                mustacheEngine.render("frontend/package.json.mustache", base));
        write(featuresRoot.resolve("auth/login").resolve("login.component.ts"),
                mustacheEngine.render("frontend/login.component.ts.mustache", base));
        write(featuresRoot.resolve("auth/login").resolve("login.component.html"),
                mustacheEngine.render("frontend/login.component.html.mustache", base));
        write(layoutRoot.resolve("sidebar").resolve("sidebar.component.ts"),
                mustacheEngine.render("frontend/sidebar.component.ts.mustache", base));
        write(layoutRoot.resolve("sidebar").resolve("sidebar.component.html"),
                mustacheEngine.render("frontend/sidebar.component.html.mustache", base));
        write(stylesRoot.resolve("styles.scss"),
                freeMarkerEngine.render("frontend/styles.scss.ftl", base));

        copyDesignSystem(appRoot.resolve("shared/ui"));
    }

    /**
     * Copies every static asset under {@code src/main/resources/design-system/} verbatim into the
     * generated Angular project's {@code src/app/shared/ui/} folder — these components use only
     * CSS variables from the generated {@code styles.scss}, so no per-project rendering is
     * needed on them.
     */
    private void copyDesignSystem(Path destinationRoot) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        var resources = resolver.getResources(DESIGN_SYSTEM_RESOURCE_PATTERN);
        for (var resource : resources) {
            String uri = resource.getURI().toString();
            int idx = uri.indexOf(DESIGN_SYSTEM_ROOT_PREFIX);
            if (idx < 0) continue;
            String relative = uri.substring(idx + DESIGN_SYSTEM_ROOT_PREFIX.length());
            if (relative.isEmpty() || relative.endsWith("/")) continue;
            Path destination = destinationRoot.resolve(relative);
            Files.createDirectories(destination.getParent());
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, destination);
            }
        }
    }

    // ── Infrastructure rendering ─────────────────────────────────────────────────

    private void renderInfrastructure(InfraProjection projection, Path infraRoot) throws IOException {
        Map<String, Object> base = new HashMap<>();
        base.put("infra", projection);

        write(infraRoot.resolve("docker-compose.yml"),
                freeMarkerEngine.render("infrastructure/docker-compose.yml.ftl", base));
        write(infraRoot.resolve(".env.example"),
                freeMarkerEngine.render("infrastructure/env.example.ftl", base));
        write(infraRoot.resolve("db/schema.sql"),
                freeMarkerEngine.render("infrastructure/schema.sql.ftl", base));
        write(infraRoot.resolve("frontend/Dockerfile"),
                mustacheEngine.render("infrastructure/FrontendDockerfile.mustache", base));
        write(infraRoot.resolve("frontend/nginx.conf"),
                mustacheEngine.render("infrastructure/NginxConf.mustache", base));
    }

    // ── ZIP / IO ─────────────────────────────────────────────────────────────────

    private void write(Path destination, String content) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, content, StandardCharsets.UTF_8);
    }

    private byte[] zipDirectory(Path root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
             var paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String entryName = root.relativize(file).toString().replace('\\', '/');
                ZipArchiveEntry entry = new ZipArchiveEntry(file.toFile(), entryName);
                zos.putArchiveEntry(entry);
                Files.copy(file, zos);
                zos.closeArchiveEntry();
            }
            zos.finish();
        }
        return baos.toByteArray();
    }

    private void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ex) {
            log.warn("Could not clean up working directory {}: {}", dir, ex.getMessage());
        }
    }

    // ── Reads ────────────────────────────────────────────────────────────────────

    public List<GeneratedCode> getGeneratedCode(UUID projectId) {
        return repository.findByProjectId(projectId);
    }

    public byte[] downloadCode(UUID projectId, CodeLayer layer) {
        GeneratedCode code = repository.findByProjectIdAndLayer(projectId, layer)
                .orElseThrow(() -> new CodeNotFoundException(projectId));
        if (code.getCodePath() == null
                || (code.getStatus() != CodeStatus.GENERATED && code.getStatus() != CodeStatus.APPROVED)) {
            throw new CodeNotFoundException(projectId);
        }
        return storageService.loadZip(code.getCodePath());
    }

    // ── Validate (compile self-correction loop) ──────────────────────────────────

    /**
     * Full self-correction validation:
     * <ol>
     *   <li>Presence check for every layer (ZIP exists, no FAILED layer).</li>
     *   <li>Extract the BACKEND ZIP into a temp dir and run {@code mvn compile}.</li>
     *   <li>If compilation fails, group errors by file and ask the AI Orchestrator to return
     *       the corrected source; write the fix and recompile. Repeat up to
     *       {@code codegen.validate.compile.max-attempts} times.</li>
     *   <li>If compilation eventually succeeds, re-zip the fixed backend and update the
     *       stored artifact so subsequent downloads serve the fixed code.</li>
     * </ol>
     *
     * The compile step can be disabled entirely via {@code codegen.validate.compile.enabled=false}
     * (or is auto-skipped if {@code mvn} isn't on PATH), in which case validate() falls back to
     * the deterministic presence check.
     */
    public ValidationReportDTO validate(UUID projectId) {
        List<GeneratedCode> layers = repository.findByProjectId(projectId);
        if (layers.isEmpty()) throw new CodeNotFoundException(projectId);

        List<ValidationCheckDTO> checks = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (GeneratedCode code : layers) {
            String name = "layer:" + code.getLayer().name();
            if (code.getStatus() == CodeStatus.FAILED) {
                checks.add(ValidationCheckDTO.builder().name(name).status("FAILED")
                        .message(code.getLastError()).build());
                issues.add(name + " is FAILED: " + code.getLastError());
                continue;
            }
            if (code.getStatus() == CodeStatus.GENERATING) {
                checks.add(ValidationCheckDTO.builder().name(name).status("WARNING")
                        .message("still generating").build());
                issues.add(name + " is still generating");
                continue;
            }
            if (code.getCodePath() == null || !new java.io.File(code.getCodePath()).exists()) {
                checks.add(ValidationCheckDTO.builder().name(name).status("FAILED")
                        .message("archive missing on disk").build());
                issues.add(name + " archive missing on disk");
                continue;
            }
            checks.add(ValidationCheckDTO.builder().name(name).status("PASSED")
                    .message("archive present").build());
        }

        int attemptsUsed = 0;
        GeneratedCode backend = layers.stream()
                .filter(c -> c.getLayer() == CodeLayer.BACKEND)
                .findFirst().orElse(null);

        if (backend != null && backend.getCodePath() != null
                && backend.getStatus() != CodeStatus.FAILED
                && backend.getStatus() != CodeStatus.GENERATING
                && compileValidationEnabled) {
            CompileLoopOutcome outcome = runCompileLoop(projectId, backend);
            attemptsUsed = outcome.attempts();
            checks.addAll(outcome.checks());
            issues.addAll(outcome.issues());
        } else if (backend != null && !compileValidationEnabled) {
            checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("WARNING")
                    .message("compile-validation disabled via codegen.validate.compile.enabled=false").build());
        }

        return ValidationReportDTO.builder()
                .projectId(projectId)
                .validationStatus(issues.isEmpty() ? "PASSED" : "FAILED")
                .attemptsUsed(Math.max(1, attemptsUsed))
                .checks(checks)
                .remainingIssues(issues)
                .build();
    }

    private record CompileLoopOutcome(int attempts, List<ValidationCheckDTO> checks, List<String> issues) {}

    private CompileLoopOutcome runCompileLoop(UUID projectId, GeneratedCode backend) {
        List<ValidationCheckDTO> checks = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        if (!mavenRunner.isAvailable()) {
            checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("WARNING")
                    .message("`mvn` not on PATH — skipping compile-validation").build());
            return new CompileLoopOutcome(0, checks, issues);
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("astyann-validate-" + projectId + "-");
            unzipInto(storageService.loadZip(backend.getCodePath()), workDir);

            int attempt;
            for (attempt = 1; attempt <= Math.max(1, compileMaxAttempts); attempt++) {
                var result = mavenRunner.compile(workDir);
                if (result.success()) {
                    checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("PASSED")
                            .message("compiled successfully on attempt " + attempt).build());
                    // Persist the (possibly-fixed) backend so downloads reflect the fix.
                    if (attempt > 1) {
                        byte[] fixedZip = zipDirectory(workDir);
                        String newPath = storageService.saveZip(projectId, CodeLayer.BACKEND, fixedZip);
                        backend.setCodePath(newPath);
                        backend.setLastError(null);
                        repository.save(backend);
                    }
                    return new CompileLoopOutcome(attempt, checks, issues);
                }

                var errors = mavenRunner.parseErrors(result.output());
                if (errors.isEmpty()) {
                    issues.add("Backend failed to compile but no error rows were parseable from Maven output.");
                    checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("FAILED")
                            .message("compile failed; no parseable errors").build());
                    return new CompileLoopOutcome(attempt, checks, issues);
                }

                Set<Path> filesToFix = new HashSet<>();
                for (var err : errors) filesToFix.add(err.file());
                for (Path file : filesToFix) {
                    if (!Files.exists(file)) continue;
                    List<String> perFile = errors.stream().filter(e -> e.file().equals(file))
                            .map(MavenRunner.CompileErrorRow::formatted).toList();
                    tryFixFileWithAi(file, perFile);
                }
                checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("WARNING")
                        .message("attempt " + attempt + ": " + errors.size() + " errors, applying AI fix").build());
            }

            issues.add("Backend still fails to compile after " + compileMaxAttempts + " AI fix attempts.");
            checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("FAILED")
                    .message("exhausted retries").build());
            return new CompileLoopOutcome(attempt - 1, checks, issues);

        } catch (Exception ex) {
            log.error("Compile self-correction loop errored for project {}: {}", projectId, ex.getMessage(), ex);
            issues.add("Compile self-correction loop errored: " + ex.getMessage());
            checks.add(ValidationCheckDTO.builder().name("compile:BACKEND").status("FAILED")
                    .message(ex.getMessage()).build());
            return new CompileLoopOutcome(0, checks, issues);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private void tryFixFileWithAi(Path file, List<String> errorLines) {
        try {
            String current = filePatcher.read(file);
            var response = aiOrchestratorClient.infer(new AiOrchestratorClient.InferenceRequest(
                    aiModel,
                    promptBuilder.systemPromptForCompileFix(),
                    promptBuilder.userPromptForCompileFix(current, errorLines)));
            String fixed = response == null ? null : response.content();
            if (fixed == null || fixed.isBlank()) {
                log.warn("AI returned empty fix for {}", file.getFileName());
                return;
            }
            String cleaned = stripCodeFences(fixed);
            if (!filePatcher.replaceEntireFile(file, cleaned)) {
                log.warn("AI-proposed fix for {} did not parse — leaving file untouched.", file.getFileName());
            }
        } catch (Exception ex) {
            log.warn("Could not fix {} via AI: {}", file.getFileName(), ex.getMessage());
        }
    }

    private String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private void unzipInto(byte[] zipBytes, Path destination) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes))) {
            org.apache.commons.compress.archivers.ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destination.resolve(entry.getName()).normalize();
                if (!out.startsWith(destination)) {
                    throw new IOException("Zip slip detected: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out);
                }
            }
        }
    }

    // ── Approve ──────────────────────────────────────────────────────────────────

    public record ApproveOutcome(List<UUID> snapshotIds, int updatedCount, boolean allLayersApproved) {}

    @Transactional
    public ApproveOutcome approve(UUID projectId, List<CodeLayer> layers, String approvalComment) {
        List<GeneratedCode> projectCode = repository.findByProjectId(projectId);
        if (projectCode.isEmpty()) throw new CodeNotFoundException(projectId);

        List<GeneratedCode> targets = (layers == null || layers.isEmpty())
                ? projectCode
                : projectCode.stream().filter(c -> layers.contains(c.getLayer())).toList();

        List<GeneratedCode> approvable = targets.stream()
                .filter(c -> c.getStatus() == CodeStatus.GENERATED || c.getStatus() == CodeStatus.PENDING_APPROVAL)
                .toList();
        if (approvable.isEmpty()) {
            throw new IllegalStateException(
                    "No GENERATED / PENDING_APPROVAL layers to approve for project " + projectId);
        }

        approvable.forEach(c -> {
            c.setStatus(CodeStatus.APPROVED);
            c.setFeedback(null);
        });
        repository.saveAll(approvable);

        String reason = (approvalComment != null && !approvalComment.isBlank())
                ? approvalComment : "Code approved";
        List<UUID> snapshotIds = new ArrayList<>();
        for (GeneratedCode c : approvable) {
            UUID snapId = attemptSnapshot(projectId, c, reason);
            if (snapId != null) snapshotIds.add(snapId);
        }
        repository.saveAll(approvable);

        boolean allApproved = repository.findByProjectId(projectId).stream()
                .allMatch(c -> c.getStatus() == CodeStatus.APPROVED);
        return new ApproveOutcome(snapshotIds, approvable.size(), allApproved);
    }

    // ── Change request ───────────────────────────────────────────────────────────

    @Transactional
    public GeneratedCode submitChangeRequest(UUID projectId, CodeLayer layer, String instructions) {
        GeneratedCode code = repository.findByProjectIdAndLayer(projectId, layer)
                .orElseThrow(() -> new CodeNotFoundException(projectId));
        code.setFeedback(instructions);
        code.setStatus(CodeStatus.PENDING_APPROVAL);
        return repository.save(code);
    }

    // ── Regenerate ───────────────────────────────────────────────────────────────

    public record RegenerateResult(List<GeneratedCode> layers, Map<CodeLayer, UUID> previousVersionByLayer) {}

    public RegenerateResult regenerate(UUID projectId, List<CodeLayer> layers) {
        List<GeneratedCode> projectCode = repository.findByProjectId(projectId);
        if (projectCode.isEmpty()) throw new CodeNotFoundException(projectId);

        List<GeneratedCode> targets = (layers == null || layers.isEmpty())
                ? projectCode.stream().filter(c -> c.getStatus() != CodeStatus.APPROVED).toList()
                : projectCode.stream().filter(c -> layers.contains(c.getLayer())).toList();

        for (GeneratedCode c : targets) {
            if (c.getStatus() == CodeStatus.APPROVED) {
                throw new IllegalStateException("Cannot regenerate APPROVED layer " + c.getLayer()
                        + " directly. Submit a change-request first.");
            }
            if (c.getStatus() == CodeStatus.GENERATING) {
                throw new IllegalStateException(c.getLayer() + " is still generating.");
            }
        }

        verifyPcsfApproved(projectId);
        verifyAllDocumentsApproved(projectId);

        Map<CodeLayer, UUID> previousByLayer = new HashMap<>();
        List<GeneratedCode> placeholders = new ArrayList<>();
        for (GeneratedCode c : targets) {
            previousByLayer.put(c.getLayer(), findActiveSnapshotId(projectId, c.getCodeId()));
            c.setStatus(CodeStatus.GENERATING);
            c.setLastError(null);
            GeneratedCode placeholder = repository.save(c);
            placeholders.add(placeholder);
            codeExecutor.execute(() -> {
                try {
                    generateLayer(projectId, placeholder.getCodeId(), placeholder.getLayer());
                } catch (Exception ex) {
                    log.error("Unexpected failure regenerating {}: {}", placeholder.getLayer(), ex.getMessage(), ex);
                    markFailed(placeholder.getCodeId(), ex.getMessage());
                }
            });
        }
        return new RegenerateResult(placeholders, previousByLayer);
    }

    // ── Activate archived version ────────────────────────────────────────────────

    @Transactional
    public GeneratedCode activateVersion(UUID projectId, CodeLayer layer, UUID snapshotId) {
        GeneratedCode code = repository.findByProjectIdAndLayer(projectId, layer)
                .orElseThrow(() -> new CodeNotFoundException(projectId));

        CodeVersionArchive archive = archiveRepository
                .findBySnapshotIdAndCodeId(snapshotId, code.getCodeId())
                .orElseThrow(() -> new CodeVersionNotFoundException(code.getCodeId(), snapshotId));

        code.setCodePath(archive.getCodePath());
        code.setStatus(CodeStatus.APPROVED);
        code.setLastError(null);
        code.setFeedback(null);
        code.setSnapshotId(snapshotId);
        GeneratedCode saved = repository.save(code);

        try {
            versionServiceClient.activateSnapshot(snapshotId);
        } catch (Exception ex) {
            log.warn("Restored codeId={} to snapshotId={} but could not flip its active flag " +
                    "in VersionService: {}", code.getCodeId(), snapshotId, ex.getMessage());
        }
        return saved;
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────────

    private UUID attemptSnapshot(UUID projectId, GeneratedCode code, String triggerReason) {
        try {
            var snap = versionServiceClient.createSnapshot(projectId, new VersionServiceClient.CreateSnapshotRequest(
                    "CODE", null, null, triggerReason, code.getCodePath(),
                    code.getCodeId(), code.getLayer().name()));
            if (snap != null && snap.getData() != null) {
                UUID snapId = snap.getData().snapId();
                code.setSnapshotId(snapId);
                archiveRepository.save(CodeVersionArchive.builder()
                        .snapshotId(snapId)
                        .projectId(projectId)
                        .codeId(code.getCodeId())
                        .layer(code.getLayer())
                        .codePath(code.getCodePath())
                        .build());
                return snapId;
            }
        } catch (Exception ex) {
            log.error("Snapshot creation failed for codeId={}: {}", code.getCodeId(), ex.getMessage());
        }
        return null;
    }

    private UUID findActiveSnapshotId(UUID projectId, UUID codeId) {
        try {
            var response = versionServiceClient.listSnapshots(projectId);
            if (response == null || response.getData() == null) return null;
            return response.getData().stream()
                    .filter(s -> codeId.equals(s.codeId()) && s.active())
                    .map(VersionServiceClient.SnapshotDTO::snapId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Could not look up previous version for codeId={}: {}", codeId, ex.getMessage());
            return null;
        }
    }

    // ── Gates ────────────────────────────────────────────────────────────────────

    private Pcsf fetchPcsf(UUID projectId) {
        ApiResponse<Pcsf> response;
        try {
            response = requirementServiceClient.getPcsf(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach RequirementService.", ex);
        }
        if (response == null || response.getData() == null) {
            throw new PcsfNotApprovedException(projectId);
        }
        return response.getData();
    }

    private void verifyPcsfApproved(UUID projectId) {
        ApiResponse<RequirementServiceClient.PcsfStatusPayload> response;
        try {
            response = requirementServiceClient.getPcsfStatus(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach RequirementService.", ex);
        }
        if (response == null || response.getData() == null
                || !"APPROVED".equals(response.getData().pcsfStatus())) {
            throw new PcsfNotApprovedException(projectId);
        }
    }

    private void verifyAllDocumentsApproved(UUID projectId) {
        ApiResponse<DocumentServiceClient.DocumentListData> response;
        try {
            response = documentServiceClient.list(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach DocumentService.", ex);
        }
        List<DocumentServiceClient.DocumentItem> documents =
                response != null && response.getData() != null ? response.getData().documents() : List.of();
        if (documents.isEmpty() || documents.stream().anyMatch(d -> !"APPROVED".equals(d.status()))) {
            throw new DocumentsNotApprovedException(projectId);
        }
    }

    private void markFailed(UUID codeId, String error) {
        repository.findById(codeId).ifPresent(code -> {
            code.setStatus(CodeStatus.FAILED);
            code.setLastError(error);
            repository.save(code);
        });
    }

    private String toPascalCase(String input) {
        if (input == null || input.isBlank()) return "App";
        StringBuilder sb = new StringBuilder();
        for (String word : input.trim().split("[\\s_\\-.]+")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? "App" : sb.toString();
    }
}
