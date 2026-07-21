package afb.astyann.codegeneration.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Pcsf {

    // Section 1 — Project Identity
    private PcsfProject project;

    // Section 2 — Actors
    @Builder.Default private List<PcsfActor>   actors       = new ArrayList<>();
    private PcsfPublicAccess publicAccess;

    // Section 3 — Functional Scope
    @Builder.Default private List<PcsfModule>  modules      = new ArrayList<>();
    private PcsfConditionalFeatures conditionalFeatures;

    // Section 4 — Entities (AI_INFERRED)
    @Builder.Default private List<PcsfEntity>       entities      = new ArrayList<>();
    @Builder.Default private List<PcsfRelationship> relationships = new ArrayList<>();

    // Section 5 — Business Rules (AI_INFERRED)
    @Builder.Default private List<PcsfBusinessRule>      businessRules      = new ArrayList<>();
    @Builder.Default private List<PcsfStatusMachine>     statusMachines     = new ArrayList<>();
    @Builder.Default private List<PcsfAccessControlRule> accessControlRules = new ArrayList<>();
    @Builder.Default private List<PcsfErrorCode>         errorCodes         = new ArrayList<>();

    // Section 5b — API Endpoints (AI_INFERRED, one entry per use-case-derived REST operation)
    @Builder.Default private List<PcsfApiEndpoint>       endpoints          = new ArrayList<>();

    // Section 6 — NFRs
    private PcsfNonFunctionalRequirements nonFunctionalRequirements;

    // Section 7 — User Interface (AI_INFERRED)
    private PcsfUserInterface userInterface;

    // Sections 8-10 — HARDCODED
    private PcsfApiConfig            apiConfig;
    private PcsfDatabaseConfig       databaseConfig;
    private PcsfInfrastructureConfig infrastructureConfig;

    // Validation block
    @Builder.Default private PcsfValidation validation = new PcsfValidation();
}
