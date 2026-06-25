package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.domain.PcsfStatus;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.pcsf.dto.PcsfValidateResponse;
import afb.astyann.projectservice.pcsf.model.*;
import afb.astyann.projectservice.pcsf.model.enums.FieldStatus;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PcsfValidationService {

    private final ProjectRepository projectRepository;
    private final ObjectMapper      objectMapper;

    @Transactional
    public PcsfValidateResponse validate(UUID projectId) {
        Project project = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));

        if (project.getPcsfJson() == null) {
            return PcsfValidateResponse.builder().valid(false).pcsfStatus("DRAFT")
                    .errors(List.of("PCSF not initialised")).warnings(List.of()).build();
        }

        Pcsf pcsf;
        try {
            pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);
        } catch (Exception ex) {
            return PcsfValidateResponse.builder().valid(false).pcsfStatus("DRAFT")
                    .errors(List.of("PCSF parse error: " + ex.getMessage())).warnings(List.of()).build();
        }

        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ── VR-01 to VR-03: Project identity ──────────────────────────────────
        if (notConfirmed(pcsf.getProject().getName()))        errors.add("VR-01: project.name not confirmed");
        if (notConfirmed(pcsf.getProject().getDescription())) errors.add("VR-02: project.description not confirmed");
        if (notConfirmed(pcsf.getProject().getDisplayName())) errors.add("VR-03: project.displayName not confirmed");

        // ── VR-04 to VR-05: Actors ────────────────────────────────────────────
        if (pcsf.getActors() == null || pcsf.getActors().isEmpty()) {
            errors.add("VR-04: actors list is empty");
        } else {
            for (PcsfActor actor : pcsf.getActors()) {
                if (notConfirmed(actor.getName()))        errors.add("VR-05: actor " + actor.getId() + " name not confirmed");
                if (notConfirmed(actor.getType()))        errors.add("VR-05: actor " + actor.getId() + " type not confirmed");
                if (notConfirmed(actor.getDescription())) errors.add("VR-05: actor " + actor.getId() + " description not confirmed");
            }
        }

        // ── VR-06 to VR-08: Modules and use cases ─────────────────────────────
        if (pcsf.getModules() == null || pcsf.getModules().isEmpty()) {
            errors.add("VR-06: modules list is empty");
        } else {
            for (PcsfModule mod : pcsf.getModules()) {
                if (notConfirmed(mod.getName()))             errors.add("VR-07: module " + mod.getId() + " name not confirmed");
                if (notConfirmed(mod.getDescription()))      errors.add("VR-07: module " + mod.getId() + " description not confirmed");
                if (listNotConfirmed(mod.getCrudOperations())) errors.add("VR-07: module " + mod.getId() + " crudOperations not confirmed");
                if (mod.getUseCases() != null) {
                    for (PcsfUseCase uc : mod.getUseCases()) {
                        if (notConfirmed(uc.getPreconditions()))       errors.add("VR-08: UC " + uc.getId() + " preconditions not confirmed");
                        if (notConfirmed(uc.getPostconditions()))      errors.add("VR-08: UC " + uc.getId() + " postconditions not confirmed");
                        if (listNotConfirmed(uc.getMainScenario()))    errors.add("VR-08: UC " + uc.getId() + " mainScenario not confirmed");
                        if (notConfirmed(uc.getAlternativeScenario())) errors.add("VR-08: UC " + uc.getId() + " alternativeScenario not confirmed");
                    }
                }
            }
        }

        // ── VR-09: Use case actor refs ────────────────────────────────────────
        var actorIds = pcsf.getActors() == null ? List.of() :
                pcsf.getActors().stream().map(PcsfActor::getId).collect(Collectors.toList());
        if (pcsf.getModules() != null) {
            pcsf.getModules().stream()
                    .filter(m -> m.getUseCases() != null)
                    .flatMap(m -> m.getUseCases().stream())
                    .filter(uc -> uc.getActorId() != null && !actorIds.contains(uc.getActorId()))
                    .forEach(uc -> errors.add("VR-09: UC " + uc.getId() + " actor ref " + uc.getActorId() + " not found"));
        }

        // ── VR-15 to VR-18: Entity model ──────────────────────────────────────
        if (pcsf.getEntities() == null || pcsf.getEntities().isEmpty()) {
            errors.add("VR-15: entities list is empty");
        } else {
            for (PcsfEntity ent : pcsf.getEntities()) {
                if (notConfirmed(ent.getName()))  errors.add("VR-16: entity " + ent.getId() + " name not confirmed");
                if (ent.getAttributes() == null || ent.getAttributes().stream()
                        .noneMatch(a -> a.getName() != null && a.getName().getStatus() == FieldStatus.CONFIRMED)) {
                    errors.add("VR-17: entity " + ent.getId() + " has no confirmed attributes");
                }
            }
            List<String> names = pcsf.getEntities().stream()
                    .filter(e -> e.getName() != null && e.getName().getValue() != null)
                    .map(e -> e.getName().getValue()).toList();
            if (names.size() != names.stream().distinct().count()) {
                errors.add("VR-18: duplicate entity names detected");
            }
        }

        // ── VR-20 to VR-21: Relationships ─────────────────────────────────────
        if (pcsf.getRelationships() != null) {
            var entityIds = pcsf.getEntities() == null ? List.of() :
                    pcsf.getEntities().stream().map(PcsfEntity::getId).collect(Collectors.toList());
            for (PcsfRelationship rel : pcsf.getRelationships()) {
                if (!entityIds.contains(rel.getFromEntityId()) || !entityIds.contains(rel.getToEntityId())) {
                    errors.add("VR-20: relationship " + rel.getId() + " references unknown entity");
                }
                if (notConfirmed(rel.getCardinality())) errors.add("VR-21: relationship " + rel.getId() + " cardinality not confirmed");
                if (notConfirmed(rel.getOptionality())) errors.add("VR-21: relationship " + rel.getId() + " optionality not confirmed");
            }
        }

        // ── VR-24 to VR-25: Business rules and ACL ────────────────────────────
        if (pcsf.getBusinessRules() != null) {
            pcsf.getBusinessRules().stream()
                    .filter(br -> notConfirmed(br.getDescription()))
                    .forEach(br -> warnings.add("VR-24: BR " + br.getId() + " description not confirmed — review recommended"));
        }
        if (pcsf.getAccessControlRules() != null) {
            pcsf.getAccessControlRules().stream()
                    .filter(acl -> listNotConfirmed(acl.getAllowedRoles()))
                    .forEach(acl -> warnings.add("VR-25: ACL " + acl.getId() + " allowedRoles not confirmed"));
        }

        // ── VR-26: ACL roles match actor spring security roles ────────────────
        List<String> definedRoles = pcsf.getActors() == null ? List.of() :
                pcsf.getActors().stream()
                        .filter(a -> a.getSpringSecurityRole() != null && a.getSpringSecurityRole().getValue() != null)
                        .map(a -> a.getSpringSecurityRole().getValue())
                        .collect(Collectors.toList());
        if (pcsf.getAccessControlRules() != null && !definedRoles.isEmpty()) {
            pcsf.getAccessControlRules().stream()
                    .filter(acl -> acl.getAllowedRoles() != null && acl.getAllowedRoles().getValue() != null)
                    .forEach(acl -> {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) acl.getAllowedRoles().getValue();
                        roles.stream().filter(r -> !definedRoles.contains(r))
                                .forEach(r -> warnings.add("VR-26: role " + r + " in ACL-" + acl.getId() + " not in actor roles"));
                    });
        }

        // ── VR-27: Gate rule ──────────────────────────────────────────────────
        boolean valid = errors.isEmpty();

        if (valid) {
            pcsf.getValidation().setGenerationReady(true);
            project.setPcsfStatus(PcsfStatus.VALIDATED);
            try { project.setPcsfJson(objectMapper.writeValueAsString(pcsf)); } catch (Exception ignored) {}
            projectRepository.save(project);
            log.info("PCSF validated and locked for project={}", projectId);
        }

        return PcsfValidateResponse.builder()
                .valid(valid)
                .pcsfStatus(project.getPcsfStatus().name())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private boolean notConfirmed(FieldValue<?> fv) {
        return fv == null || fv.getValue() == null || fv.getStatus() != FieldStatus.CONFIRMED;
    }

    private boolean listNotConfirmed(FieldValue<?> fv) {
        if (fv == null || fv.getValue() == null) return true;
        if (fv.getValue() instanceof List<?> list) return list.isEmpty();
        return false;
    }
}
