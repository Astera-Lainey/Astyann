package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PcsfInitialiserService {

    private final RequirementRepository requirementRepository;
    private final ObjectMapper          objectMapper;

    @Transactional
    public void initialise(Requirement requirement) {
        String name = requirement.getProjectTitle();
        PcsfDerivedNames derived = buildDerivedNames(name);

        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder()
                        .name(FieldValue.<String>builder()
                                .value(name)
                                .source(FieldSource.HARDCODED)
                                .status(FieldStatus.CONFIRMED)
                                .build())
                        .description(FieldValue.<String>builder()
                                .value(requirement.getProjectDescription())
                                .source(FieldSource.HARDCODED)
                                .status(requirement.getProjectDescription() != null
                                        && !requirement.getProjectDescription().isBlank()
                                        ? FieldStatus.CONFIRMED : FieldStatus.MISSING)
                                .build())
                        .displayName(FieldValue.<String>builder()
                                .status(FieldStatus.MISSING)
                                .build())
                        .organisationName("Afriland First Bank")
                        .derived(derived)
                        .build())
                .publicAccess(new PcsfPublicAccess())
                .conditionalFeatures(new PcsfConditionalFeatures())
                .nonFunctionalRequirements(buildDefaultNfr())
                .userInterface(new PcsfUserInterface())
                .apiConfig(new PcsfApiConfig())
                .databaseConfig(PcsfDatabaseConfig.builder()
                        .name(derived.getDatabaseName())
                        .user(derived.getDatabaseUser())
                        .build())
                .infrastructureConfig(new PcsfInfrastructureConfig())
                .validation(new PcsfValidation())
                .build();

        try {
            requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            requirement.setPcsfStatus(PcsfStatus.DRAFT);
            requirementRepository.save(requirement);
            log.debug("PCSF initialised for requirement={}", requirement.getRequirementId());
        } catch (Exception ex) {
            log.error("Failed to serialise PCSF for requirement={}", requirement.getRequirementId(), ex);
        }
    }

    private PcsfDerivedNames buildDerivedNames(String name) {
        String kebab = toKebabCase(name);
        String snake = toSnakeCase(name);
        String pkg   = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return PcsfDerivedNames.builder()
                .mavenArtifactId(kebab)
                .javaRootPackage("com.afriland." + pkg)
                .angularProjectName(kebab)
                .databaseName(snake + "_db")
                .databaseUser(snake + "_user")
                .build();
    }

    private PcsfNonFunctionalRequirements buildDefaultNfr() {
        return PcsfNonFunctionalRequirements.builder()
                .locale(FieldValue.<String>builder()
                        .value("en").source(FieldSource.DEFAULT).status(FieldStatus.CONFIRMED).build())
                .build();
    }

    private String toKebabCase(String s) {
        return s.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
    }

    private String toSnakeCase(String s) {
        return s.toLowerCase().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_]", "");
    }
}
