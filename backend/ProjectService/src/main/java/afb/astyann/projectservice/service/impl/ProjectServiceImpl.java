package afb.astyann.projectservice.service.impl;

import afb.astyann.projectservice.client.*;
import afb.astyann.projectservice.domain.GenerationType;
import afb.astyann.projectservice.domain.GuidedQuestion;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.domain.ProjectStatus;
import afb.astyann.projectservice.dto.*;
import afb.astyann.projectservice.exception.InvalidFileFormatException;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.repository.GuidedQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import afb.astyann.projectservice.service.IProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ProjectRepository           projectRepository;
    private final GuidedQuestionRepository    guidedQuestionRepository;
    private final AIServiceClient             aiServiceClient;
    private final RequirementsServiceClient   requirementsClient;
    private final DocumentServiceClient       documentClient;
    private final UMLServiceClient            umlClient;
    private final CodeGenServiceClient        codeGenClient;
    private final DeploymentServiceClient     deploymentClient;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public ProjectDTO createProject(UUID userId, CreateProjectDTO dto, MultipartFile document) {
        log.info("Creating project for user={} title={}", userId, dto.getTitle());

        validateDocument(document);
        String docPath = storeDocument(document);

        Project project = Project.builder()
                .userId(userId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(ProjectStatus.ANALYZING)
                .docPath(docPath)
                .build();
        Project saved = projectRepository.save(project);
        log.debug("Project saved: id={}", saved.getProjectId());

        // Call AI service to analyze the document
        ProjectAnalysisResponseDTO analysis = callClient("ai-analyze",
                () -> aiServiceClient.analyzeProjectInformation(saved.getProjectId(), document));

        if (analysis != null) {
            saved.setProjectContext(analysis.getExtractedContext());
            if (analysis.isSufficient()) {
                saved.setStatus(ProjectStatus.COMPLETED);
            }
            // Save any guided questions the AI generated
            if (analysis.getGuidedQuestions() != null) {
                analysis.getGuidedQuestions().forEach(q ->
                        guidedQuestionRepository.save(GuidedQuestion.builder()
                                .projectId(saved.getProjectId())
                                .question(q)
                                .build()));
            }
            projectRepository.save(saved);
        }

        return toDTO(saved);
    }

    // ── Guided Questions ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<GuidedQuestionDTO> getGuidedQuestions(UUID projectId) {
        findOrThrow(projectId);
        return guidedQuestionRepository.findByProjectId(projectId).stream()
                .map(gq -> GuidedQuestionDTO.builder()
                        .gqId(gq.getGqId())
                        .question(gq.getQuestion())
                        .answer(gq.getAnswer())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public ProjectDTO submitGuidedAnswers(UUID projectId, SubmitAnswersDTO dto) {
        log.info("Submitting guided answers for projectId={}", projectId);
        Project project = findOrThrow(projectId);

        List<GuidedQuestion> questions = guidedQuestionRepository.findByProjectId(projectId);

        // Save answers to the GuidedQuestion entities
        if (dto.getAnswers() != null) {
            dto.getAnswers().forEach(item -> questions.stream()
                    .filter(q -> q.getGqId().equals(item.getGqId()))
                    .findFirst()
                    .ifPresent(q -> {
                        q.setAnswer(item.getAnswer());
                        guidedQuestionRepository.save(q);
                    }));
        }

        // Build Q&A list to send to AI service for merging
        List<AIServiceClient.MergeRequestBody.AnswerItem> answerItems = questions.stream()
                .filter(q -> q.getAnswer() != null)
                .map(q -> new AIServiceClient.MergeRequestBody.AnswerItem(q.getQuestion(), q.getAnswer()))
                .collect(Collectors.toList());

        AIServiceClient.MergeRequestBody mergeBody = new AIServiceClient.MergeRequestBody(
                projectId, project.getProjectContext(), answerItems);

        ProjectAnalysisResponseDTO merged = callClient("ai-merge",
                () -> aiServiceClient.mergeDocumentAndAnswers(projectId, mergeBody));

        if (merged != null) {
            project.setProjectContext(merged.getExtractedContext());
            project.setStatus(ProjectStatus.COMPLETED);
            projectRepository.save(project);
        }

        return toDTO(project);
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
