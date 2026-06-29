package afb.astyann.requirementservice.service;

import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.dto.ApproveResponse;
import afb.astyann.requirementservice.dto.ChangeRequestResponse;
import afb.astyann.requirementservice.exception.RequirementNotFoundException;
import afb.astyann.requirementservice.repository.RequirementRepository;
import afb.astyann.requirementservice.service.pcsf.AiInferencePcsfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RequirementGenerationService {

    private final RequirementRepository requirementRepository;
    private final AiInferencePcsfService inferenceService;

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

        // RAG indexing stub — RAG service (port 8089 /index endpoint) is not yet built.
        // This is a graceful no-op; when the RAG service is implemented, add a Feign call here.
        log.info("Requirements approved for projectId={}. RAG indexing deferred (service not yet available).",
                projectId);

        return ApproveResponse.builder()
                .status("APPROVED")
                .message("Requirements approved. RAG indexing will be triggered once the RAG service is available.")
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
