package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.domain.PcsfStatus;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.pcsf.model.*;
import afb.astyann.projectservice.pcsf.model.enums.FieldSource;
import afb.astyann.projectservice.pcsf.model.enums.FieldStatus;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PcsfInitialiserService {

    private final ProjectRepository projectRepository;
    private final ObjectMapper      objectMapper;

    @Transactional
    public void initialise(Project project) {
        String name = project.getTitle();
        PcsfDerivedNames derived = buildDerivedNames(name);

        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder()
                        .name(FieldValue.<String>builder()
                                .value(name)
                                .source(FieldSource.HARDCODED)
                                .status(FieldStatus.CONFIRMED)
                                .build())
                        .description(FieldValue.<String>builder()
                                .value(project.getDescription())
                                .source(FieldSource.HARDCODED)
                                .status(project.getDescription() != null && !project.getDescription().isBlank()
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
            project.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            project.setPcsfStatus(PcsfStatus.DRAFT);
            projectRepository.save(project);
            log.debug("PCSF initialised for project={}", project.getProjectId());
        } catch (Exception ex) {
            log.error("Failed to serialise PCSF for project={}", project.getProjectId(), ex);
        }
    }

    private PcsfDerivedNames buildDerivedNames(String name) {
        String kebab  = toKebabCase(name);
        String snake  = toSnakeCase(name);
        String pkg    = name.toLowerCase().replaceAll("[^a-z0-9]", "");
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
