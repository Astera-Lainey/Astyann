package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.dto.pcsf.PcsfValidateResponse;
import afb.astyann.requirementservice.exception.RequirementNotFoundException;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PcsfValidationService {

    private final RequirementRepository requirementRepository;
    private final ObjectMapper          objectMapper;

    @Transactional
    public PcsfValidateResponse validate(UUID projectId) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));

        if (requirement.getPcsfJson() == null) {
            return PcsfValidateResponse.builder()
                    .valid(false)
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .errors(List.of("PCSF has not been generated yet"))
                    .build();
        }

        if (requirement.getPcsfStatus() == PcsfStatus.DRAFT
                || requirement.getPcsfStatus() == PcsfStatus.INFERRING) {
            return PcsfValidateResponse.builder()
                    .valid(false)
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .errors(List.of("PCSF is not ready for validation yet (status: "
                            + requirement.getPcsfStatus() + ")"))
                    .build();
        }

        try {
            Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
            List<String> errors   = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // VR-01: project name required
            if (pcsf.getProject() == null || isBlankFv(pcsf.getProject().getName()))
                errors.add("VR-01: Project name is required.");

            // VR-02: project description required
            if (pcsf.getProject() == null || isBlankFv(pcsf.getProject().getDescription()))
                errors.add("VR-02: Project description is required.");

            // VR-03: at least one actor
            if (pcsf.getActors() == null || pcsf.getActors().isEmpty())
                errors.add("VR-03: At least one actor is required.");

            // VR-04: at least one module
            if (pcsf.getModules() == null || pcsf.getModules().isEmpty())
                errors.add("VR-04: At least one module is required.");

            // VR-05: all actors must have a type
            if (pcsf.getActors() != null) {
                pcsf.getActors().forEach(a -> {
                    if (isBlankFv(a.getType()))
                        errors.add("VR-05: Actor " + a.getId() + " is missing a type.");
                });
            }

            // VR-06: use-cases must reference a valid actor
            if (pcsf.getActors() != null && pcsf.getModules() != null) {
                var actorIds = pcsf.getActors().stream().map(PcsfActor::getId).toList();
                pcsf.getModules().stream()
                        .filter(m -> m.getUseCases() != null)
                        .flatMap(m -> m.getUseCases().stream())
                        .filter(uc -> uc.getActorId() != null && !actorIds.contains(uc.getActorId()))
                        .forEach(uc -> errors.add("VR-06: Use-case " + uc.getId()
                                + " references unknown actor " + uc.getActorId()));
            }

            // VR-07: each use-case must have preconditions, postconditions, main scenario
            if (pcsf.getModules() != null) {
                pcsf.getModules().stream()
                        .filter(m -> m.getUseCases() != null)
                        .flatMap(m -> m.getUseCases().stream())
                        .forEach(uc -> {
                            if (isBlankFv(uc.getPreconditions()))
                                errors.add("VR-07a: Use-case " + uc.getId() + " missing preconditions.");
                            if (isBlankFv(uc.getPostconditions()))
                                errors.add("VR-07b: Use-case " + uc.getId() + " missing postconditions.");
                            if (isListFvEmpty(uc.getMainScenario()))
                                errors.add("VR-07c: Use-case " + uc.getId() + " missing main scenario steps.");
                            // alternativeScenario is optional — no VR-08 check
                        });
            }

            boolean valid = errors.isEmpty();
            if (valid) {
                requirement.setPcsfStatus(PcsfStatus.VALIDATED);
                requirementRepository.save(requirement);
                log.info("PCSF validated for requirement={}", requirement.getRequirementId());
            }

            return PcsfValidateResponse.builder()
                    .valid(valid)
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .errors(errors)
                    .warnings(warnings)
                    .build();

        } catch (Exception ex) {
            log.error("Validation failed for requirement={}", requirement.getRequirementId(), ex);
            return PcsfValidateResponse.builder()
                    .valid(false)
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .errors(List.of("Validation error: " + ex.getMessage()))
                    .build();
        }
    }

    private boolean isBlankFv(FieldValue<?> fv) {
        if (fv == null || fv.getValue() == null) return true;
        return fv.getValue() instanceof String s && s.isBlank();
    }

    private boolean isListFvEmpty(FieldValue<?> fv) {
        if (fv == null || fv.getValue() == null) return true;
        if (fv.getValue() instanceof List<?> list) return list.isEmpty();
        return false;
    }
}
