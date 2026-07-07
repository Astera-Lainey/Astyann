package afb.astyann.requirementservice.service;

import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.repository.RequirementRepository;
import afb.astyann.requirementservice.service.pcsf.DocumentExtractionService;
import afb.astyann.requirementservice.service.pcsf.PcsfInitialiserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RequirementBackgroundService {

    private final RequirementRepository  requirementRepository;
    private final PcsfInitialiserService pcsfInitialiserService;
    private final DocumentExtractionService documentExtractionService;

    @Async("pcsfExecutor")
    public void initializePipelineAsync(UUID projectId, String projectTitle,
                                        String projectDescription,
                                        String projectContext, String documentText) {
        log.info("Initializing requirement pipeline for projectId={}", projectId);

        Requirement requirement = Requirement.builder()
                .projectId(projectId)
                .projectTitle(projectTitle)
                .projectDescription(projectDescription)
                .projectContext(projectContext)
                .pcsfStatus(PcsfStatus.DRAFT)
                .pendingQuestionsCount(0)
                .build();
        requirementRepository.save(requirement);

        try {
            pcsfInitialiserService.initialise(requirement);
            documentExtractionService.extract(requirement, documentText);
        } catch (Exception ex) {
            log.error("Requirement pipeline initialization failed for projectId={}", projectId, ex);
            requirement.setPcsfStatus(PcsfStatus.FAILED);
            requirementRepository.save(requirement);
        }
    }
}
