package afb.astyann.projectservice.service;

import afb.astyann.projectservice.client.AIServiceClient;
import afb.astyann.projectservice.domain.PcsfStatus;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.domain.ProjectStatus;
import afb.astyann.projectservice.dto.ProjectAnalysisResponseDTO;
import afb.astyann.projectservice.pcsf.service.DocumentExtractionService;
import afb.astyann.projectservice.repository.ProjectRepository;
import afb.astyann.projectservice.util.ByteArrayMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectBackgroundService {

    private final AIServiceClient           aiServiceClient;
    private final DocumentExtractionService documentExtractionService;
    private final ProjectRepository         projectRepository;

    @Async("pcsfExecutor")
    public void processDocumentAsync(UUID projectId,
                                     byte[] docBytes,
                                     String originalFilename,
                                     String contentType) {
        log.info("Background processing started for project={}", projectId);
        try {
            Project project = projectRepository.findByProjectId(projectId)
                    .orElseThrow(() ->
                            new RuntimeException("Project not found: " + projectId));

            MultipartFile syntheticFile = new ByteArrayMultipartFile(
                    docBytes, originalFilename, contentType);

            ProjectAnalysisResponseDTO analysis = null;
            try {
                analysis = aiServiceClient.analyzeProjectInformation(
                        projectId, syntheticFile);
            } catch (Exception ex) {
                log.warn("AI analysis call failed for project={}: {}",
                        projectId, ex.getMessage());
            }

            if (analysis != null) {
                project.setProjectContext(analysis.getExtractedContext());
                projectRepository.save(project);
                documentExtractionService.extract(project, analysis.getDocumentText());
            } else {
                documentExtractionService.extract(project, null);
            }

            log.info("Background processing completed for project={}", projectId);

        } catch (Exception ex) {
            log.error("Background processing failed for project={}", projectId, ex);
            projectRepository.findByProjectId(projectId).ifPresent(p -> {
                p.setStatus(ProjectStatus.FAILED);
                p.setPcsfStatus(PcsfStatus.FAILED);
                projectRepository.save(p);
            });
        }
    }
}
