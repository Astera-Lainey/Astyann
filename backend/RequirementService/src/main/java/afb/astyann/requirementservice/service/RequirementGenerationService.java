package afb.astyann.requirementservice.service;

import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.dto.ApproveResponse;
import afb.astyann.requirementservice.dto.ChangeRequestResponse;
import afb.astyann.requirementservice.exception.RequirementNotFoundException;
import afb.astyann.requirementservice.repository.RequirementRepository;
import afb.astyann.requirementservice.service.pcsf.AiInferencePcsfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@Slf4j
public class RequirementGenerationService {

    private final RequirementRepository  requirementRepository;
    private final AiInferencePcsfService inferenceService;
    private final RagIndexingService     ragIndexingService;

    public RequirementGenerationService(RequirementRepository requirementRepository,
                                        AiInferencePcsfService inferenceService,
                                        @Lazy RagIndexingService ragIndexingService) {
        this.requirementRepository = requirementRepository;
        this.inferenceService      = inferenceService;
        this.ragIndexingService    = ragIndexingService;
    }

    @Transactional
    public ApproveResponse approve(UUID projectId) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));

        if (requirement.getPcsfStatus() != PcsfStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Requirements must be VALIDATED before approval (current status: "
                    + requirement.getPcsfStatus() + ")");
        }

        requirement.setPcsfStatus(PcsfStatus.APPROVED);
        requirementRepository.save(requirement);

        // Defer RAG indexing until after the current transaction commits so the async
        // thread reads the persisted APPROVED state (same afterCommit pattern as inference).
        UUID reqId = requirement.getRequirementId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        ragIndexingService.initializeIndexAsync(reqId);
                    }
                });
        } else {
            ragIndexingService.initializeIndexAsync(reqId);
        }

        log.info("Requirements approved for projectId={}. RAG indexing queued.", projectId);

        return ApproveResponse.builder()
                .status("APPROVED")
                .message("Requirements approved. PCSF is being indexed into the RAG knowledge base.")
                .build();
    }

    @Transactional
    public ChangeRequestResponse submitChangeRequest(UUID projectId, String instructions) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));

        if (requirement.getPcsfStatus() == PcsfStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot request changes on APPROVED requirements. Create a new project revision instead.");
        }

        String changeRequestId = UUID.randomUUID().toString();
        requirement.setChangeInstructions(instructions);
        requirement.setPcsfStatus(PcsfStatus.CHANGE_REQUESTED);
        requirementRepository.save(requirement);

        log.info("Change request submitted for projectId={} (id={})", projectId, changeRequestId);

        return ChangeRequestResponse.builder()
                .changeRequestId(changeRequestId)
                .status("CHANGE_REQUESTED")
                .message("Change request recorded. Call /regenerate to re-run AI inference with your instructions.")
                .build();
    }

    @Transactional
    public void regenerate(UUID projectId) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));

        if (requirement.getChangeInstructions() == null
                || requirement.getChangeInstructions().isBlank()) {
            throw new IllegalStateException(
                    "No change instructions found. Submit a change-request first.");
        }

        String instructions = requirement.getChangeInstructions();
        requirement.setPcsfStatus(PcsfStatus.INFERRING);
        requirementRepository.save(requirement);

        inferenceService.runReInferenceAsync(requirement.getRequirementId(), instructions);
        log.info("Regeneration triggered for projectId={}", projectId);
    }
}
