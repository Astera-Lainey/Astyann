package afb.astyann.projectservice.service.impl;

import afb.astyann.projectservice.client.*;
import afb.astyann.projectservice.domain.GenerationType;
import afb.astyann.projectservice.domain.GuidedQuestion;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.domain.ProjectStatus;
import afb.astyann.projectservice.dto.*;
import afb.astyann.projectservice.exception.InvalidFileFormatException;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.repository.ClarificationQuestionRepository;
import afb.astyann.projectservice.repository.GuidedQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import afb.astyann.projectservice.service.IProjectService;
import afb.astyann.projectservice.service.ProjectBackgroundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements IProjectService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");

    @Value("${app.upload-dir:uploads/documents}")
    private String uploadDir;

    private final ProjectRepository               projectRepository;
    private final GuidedQuestionRepository        guidedQuestionRepository;
    private final ClarificationQuestionRepository clarificationQuestionRepository;
    private final AIServiceClient                 aiServiceClient;
    private final RequirementsServiceClient  requirementsClient;
    private final DocumentServiceClient      documentClient;
    private final UMLServiceClient           umlClient;
    private final CodeGenServiceClient       codeGenClient;
    private final DeploymentServiceClient    deploymentClient;
    private final ProjectBackgroundService   projectBackgroundService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public ProjectDTO createProject(UUID userId, CreateProjectDTO dto, MultipartFile document) {
        log.info("Creating project for user={} title={}", userId, dto.getTitle());

        validateDocument(document);

        // Store document to disk first, then read the bytes back.
        // The original MultipartFile InputStream is consumed by transferTo(), so
        // we read from the saved file to pass safe bytes to the background thread.
        String docPath = storeDocument(document);

        byte[] docBytes;
        try {
            docBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(docPath));
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Could not read stored document", ex);
        }

        Project project = Project.builder()
                .userId(userId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(ProjectStatus.ANALYZING)
                .docPath(docPath)
                .build();
        Project saved = projectRepository.save(project);
        log.debug("Project saved: id={}", saved.getProjectId());

        // Fire AI processing in the background AFTER the transaction commits,
        // so the async thread can see the newly-saved project.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        projectBackgroundService.processDocumentAsync(
                                saved.getProjectId(),
                                docBytes,
                                document.getOriginalFilename(),
                                document.getContentType());
                    }
                });

        // Return 201 immediately. Angular will poll the status endpoint
        // to know when background processing finishes.
        return toDTO(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public ProjectDTO updateProject(UUID projectId, UpdateProjectDTO dto) {
        log.info("Updating project id={}", projectId);
        Project project = findOrThrow(projectId);
        if (dto.getTitle() != null)       project.setTitle(dto.getTitle());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getStatus() != null)      project.setStatus(dto.getStatus());
        return toDTO(projectRepository.save(project));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void deleteProject(UUID projectId) {
        log.info("Deleting project id={}", projectId);
        Project project = findOrThrow(projectId);
        clarificationQuestionRepository.deleteByProject(project);
        guidedQuestionRepository.deleteByProjectId(projectId);
        projectRepository.deleteByProjectId(projectId);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDTO> searchProjects(UUID userId, String query) {
        log.debug("Searching projects for user={} query={}", userId, query);
        List<Project> results = (query != null && !query.isBlank())
                ? projectRepository.findByUserIdAndTitleContaining(userId, query)
                : projectRepository.findByUserId(userId);
        return results.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProjectDTO getProjectById(UUID projectId) {
        return toDTO(findOrThrow(projectId));
    }

    // ── Trigger Generation ────────────────────────────────────────────────────

    @Override
    public void triggerGeneration(UUID projectId, GenerationType type) {
        log.info("Triggering generation type={} for project id={}", type, projectId);
        Project project = findOrThrow(projectId);
        project.setStatus(ProjectStatus.GENERATING);
        projectRepository.save(project);

        switch (type) {
            case REQUIREMENTS -> log.info("Requirements pipeline is auto-initiated on project creation.");
            case DOCUMENTS    -> callClient("documents",    () ->
                    documentClient.triggerDocumentGeneration(projectId));
            case UML          -> callClient("uml",          () ->
                    umlClient.triggerUMLGeneration(projectId));
            case CODE         -> callClient("codegen",      () ->
                    codeGenClient.triggerCodeGeneration(projectId));
            case DEPLOYMENT   -> callClient("deployment",   () ->
                    deploymentClient.triggerDeploymentGeneration(projectId));
            case FULL -> {
                callClient("documents",  () -> documentClient.triggerDocumentGeneration(projectId));
                callClient("uml",        () -> umlClient.triggerUMLGeneration(projectId));
                callClient("codegen",    () -> codeGenClient.triggerCodeGeneration(projectId));
                callClient("deployment", () -> deploymentClient.triggerDeploymentGeneration(projectId));
            }
        }
    }

    // ── File Helpers ──────────────────────────────────────────────────────────

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("A document file is required (PDF or DOCX).");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension    = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String contentType  = file.getContentType() != null ? file.getContentType() : "";

        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileFormatException("Only PDF or DOCX files are allowed.");
        }
    }

    private String storeDocument(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String filename    = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path   destination = dir.resolve(filename);
            file.transferTo(destination);
            log.debug("Document stored at: {}", destination);
            return destination.toString();
        } catch (IOException ex) {
            log.error("Failed to store document", ex);
            throw new RuntimeException("Could not store the uploaded document.", ex);
        }
    }

    // ── General Helpers ───────────────────────────────────────────────────────

    private Project findOrThrow(UUID projectId) {
        return projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));
    }

    private ProjectDTO toDTO(Project p) {
        return ProjectDTO.builder()
                .projectId(p.getProjectId())
                .userId(p.getUserId())
                .title(p.getTitle())
                .description(p.getDescription())
                .status(p.getStatus())
                .creationDate(p.getCreationDate())
                .updatedDate(p.getUpdatedDate())
                .build();
    }

    private <T> T callClient(String name, java.util.concurrent.Callable<T> call) {
        try {
            return call.call();
        } catch (Exception ex) {
            log.warn("Could not reach {} service: {}", name, ex.getMessage());
            return null;
        }
    }

    private void callClient(String name, Runnable call) {
        try {
            call.run();
        } catch (Exception ex) {
            log.warn("Could not reach {} service: {}", name, ex.getMessage());
        }
    }
}
