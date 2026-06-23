package afb.astyann.projectservice.service.impl;

import afb.astyann.projectservice.client.*;
import afb.astyann.projectservice.domain.GenerationType;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.domain.ProjectStatus;
import afb.astyann.projectservice.dto.CreateProjectDTO;
import afb.astyann.projectservice.dto.ProjectDTO;
import afb.astyann.projectservice.dto.UpdateProjectDTO;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.repository.GuidedQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import afb.astyann.projectservice.service.IProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements IProjectService {

    private final ProjectRepository           projectRepository;
    private final GuidedQuestionRepository    guidedQuestionRepository;
    private final RequirementsServiceClient   requirementsClient;
    private final DocumentServiceClient       documentClient;
    private final UMLServiceClient            umlClient;
    private final CodeGenServiceClient        codeGenClient;
    private final DeploymentServiceClient     deploymentClient;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public ProjectDTO createProject(UUID userId, CreateProjectDTO dto) {
        log.info("Creating project for user={} title={}", userId, dto.getTitle());
        Project project = Project.builder()
                .userId(userId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(ProjectStatus.ANALYZING)
                .build();
        Project saved = projectRepository.save(project);
        log.debug("Project created: id={}", saved.getProjectId());
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
        Project saved = projectRepository.save(project);
        return toDTO(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void deleteProject(UUID projectId) {
        log.info("Deleting project id={}", projectId);
        findOrThrow(projectId);
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

    // ── Trigger Generation ───────────────────────────────────────────────────

    @Override
    public void triggerGeneration(UUID projectId, GenerationType type) {
        log.info("Triggering generation type={} for project id={}", type, projectId);
        Project project = findOrThrow(projectId);
        project.setStatus(ProjectStatus.GENERATING);
        projectRepository.save(project);

        switch (type) {
            case REQUIREMENTS -> callClient("requirements", () ->
                    requirementsClient.triggerRequirementsGeneration(projectId));
            case DOCUMENTS    -> callClient("documents",    () ->
                    documentClient.triggerDocumentGeneration(projectId));
            case UML          -> callClient("uml",          () ->
                    umlClient.triggerUMLGeneration(projectId));
            case CODE         -> callClient("codegen",      () ->
                    codeGenClient.triggerCodeGeneration(projectId));
            case DEPLOYMENT   -> callClient("deployment",   () ->
                    deploymentClient.triggerDeploymentGeneration(projectId));
            case FULL -> {
                callClient("requirements", () -> requirementsClient.triggerRequirementsGeneration(projectId));
                callClient("documents",    () -> documentClient.triggerDocumentGeneration(projectId));
                callClient("uml",          () -> umlClient.triggerUMLGeneration(projectId));
                callClient("codegen",      () -> codeGenClient.triggerCodeGeneration(projectId));
                callClient("deployment",   () -> deploymentClient.triggerDeploymentGeneration(projectId));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project findOrThrow(UUID projectId) {
        return projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project not found: " + projectId));
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

    /** Calls a downstream client and logs a warning if the service is unavailable. */
    private void callClient(String serviceName, Runnable call) {
        try {
            call.run();
        } catch (Exception ex) {
            log.warn("Could not reach {} service: {}", serviceName, ex.getMessage());
        }
    }
}
