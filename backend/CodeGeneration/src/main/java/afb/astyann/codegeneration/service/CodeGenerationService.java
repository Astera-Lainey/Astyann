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
import afb.astyann.codegeneration.repository.CodeVersionArchiveRepository;
import afb.astyann.codegeneration.repository.GeneratedCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ProjectionBuilder projectionBuilder;
    private final FreeMarkerEngine freeMarkerEngine;
    private final MustacheEngine mustacheEngine;
    private final StorageService storageService;

    @Qualifier("codeExecutor")
    private final Executor codeExecutor;

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
                case BACKEND -> renderBackend(projectionBuilder.buildBackendProjection(pcsf), layerRoot);
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

    // ── Validate (deterministic; AI pass is a follow-up) ─────────────────────────

    /**
     * Deterministic self-check over the currently-GENERATED artifacts: verifies the ZIPs exist
     * and that no layer is still in FAILED. This satisfies API-CODE-03 as a working stub; the
     * design doc's "compilation / dependency" self-correction loop is an AI-driven follow-up.
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

        return ValidationReportDTO.builder()
                .projectId(projectId)
                .validationStatus(issues.isEmpty() ? "PASSED" : "FAILED")
                .attemptsUsed(1)
                .checks(checks)
                .remainingIssues(issues)
                .build();
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
