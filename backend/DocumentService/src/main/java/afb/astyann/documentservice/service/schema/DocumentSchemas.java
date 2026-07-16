package afb.astyann.documentservice.service.schema;

import afb.astyann.documentservice.domain.DocumentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registry of the per-document-type merge schema, derived directly from each template's
 * actual placeholder catalog (extracted by inspecting word/document.xml). */
public final class DocumentSchemas {

    private static final Map<DocumentType, DocumentSchema> SCHEMAS = new EnumMap<>(DocumentType.class);

    static {
        register(srs());
        register(functionalAnalysis());
        register(designDocument());
        register(deploymentGuide());
        register(architectureDocument());
        register(apiContract());
        register(userManual());
        register(dataDictionary());
    }

    private DocumentSchemas() {}

    public static DocumentSchema get(DocumentType type) {
        DocumentSchema schema = SCHEMAS.get(type);
        if (schema == null) throw new IllegalStateException("No schema registered for " + type);
        return schema;
    }

    private static void register(DocumentSchema schema) {
        SCHEMAS.put(schema.type(), schema);
    }

    private static List<ScalarField> scalars(String... paths) {
        return List.of(paths).stream().map(ScalarField::new).toList();
    }

    private static DocumentSchema srs() {
        return new DocumentSchema(DocumentType.SRS, "templates/document/srs-template.docx",
                "Produce a full Software Requirements Specification.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction.overview", "introduction.purpose", "introduction.scope",
                        "generalDescription", "designConstraints", "interfaceRequirements",
                        "appendices.acronyms", "appendices.definitions", "appendices.references",
                        "nonFunctionalAttributes.compatibility", "nonFunctionalAttributes.dataIntegrity",
                        "nonFunctionalAttributes.portability", "nonFunctionalAttributes.reliability",
                        "nonFunctionalAttributes.reusability", "nonFunctionalAttributes.scalability",
                        "nonFunctionalAttributes.security",
                        "performanceRequirements.dynamicRequirements", "performanceRequirements.errorRate",
                        "performanceRequirements.memoryCapacity", "performanceRequirements.responseTime",
                        "performanceRequirements.staticRequirements",
                        "schedule.duration", "schedule.estimatedCost"
                ),
                List.of(
                        new RepeatingGroup("req", "req", List.of("id", "title", "description", "priority")),
                        new RepeatingGroup("milestone", "milestone", List.of("phase", "duration", "description"))
                ),
                List.of());
    }

    private static DocumentSchema functionalAnalysis() {
        return new DocumentSchema(DocumentType.FUNCTIONAL_ANALYSIS, "templates/document/analysis-template.docx",
                "Produce a functional analysis document covering actors, use cases, entities, and requirements.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction", "conclusion",
                        "projectPresentation.generalPresentation", "projectPresentation.projectContext"
                ),
                List.of(
                        new RepeatingGroup("internalActor", "internalActor", List.of("name", "role")),
                        new RepeatingGroup("externalActor", "externalActor", List.of("name", "role")),
                        new RepeatingGroup("uc", "uc", List.of("title", "objective", "actors", "preconditions",
                                "mainScenario", "alternativeScenario", "postconditions")),
                        new RepeatingGroup("entity", "entity", List.of("name", "attributes", "methods")),
                        new RepeatingGroup("fr", "fr", List.of("id", "description")),
                        new RepeatingGroup("nfr", "nfr", List.of("id", "description"))
                ),
                List.of());
    }

    private static DocumentSchema designDocument() {
        return new DocumentSchema(DocumentType.DESIGN_DOCUMENT, "templates/document/design-template.docx",
                "Produce a technical design document covering modules, design method, and UI theme.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction", "conclusion", "systemObjectives", "systemInterfaces", "designConstraints",
                        "qualityPlan", "programmingStandards", "algorithms",
                        "designMethod.approach", "designMethod.justification", "designMethod.modellingLanguage",
                        "applicationTheme.primaryColour", "applicationTheme.secondaryColour",
                        "applicationTheme.fontFamily", "applicationTheme.buttonStyle"
                ),
                List.of(
                        new RepeatingGroup("module", "module", List.of("name", "description", "responsibilities")),
                        new RepeatingGroup("tool", "tool", List.of("name", "version", "purpose")),
                        new RepeatingGroup("refDoc", "refDoc", List.of("title", "description"))
                ),
                List.of());
    }

    private static DocumentSchema deploymentGuide() {
        return new DocumentSchema(DocumentType.DEPLOYMENT_GUIDE, "templates/document/deployment-guide-template.docx",
                "Produce a deployment guide covering architecture, build/deploy commands, and troubleshooting.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction.purpose", "introduction.scope", "introduction.intendedAudience",
                        "architecture.componentDescription", "architecture.dataFlowSummary", "architecture.networkDescription",
                        "buildInstructions.backendBuild", "buildInstructions.frontendBuild", "buildInstructions.dockerBuild",
                        "databaseSetup.schemaInitialisation", "databaseSetup.seedData", "databaseSetup.migrationStrategy",
                        "databaseSetup.backupCommand", "databaseSetup.restoreCommand",
                        "deployment.startCommand", "deployment.stopCommand", "deployment.logCommand", "deployment.updateProcedure",
                        "healthCheck.endpoint", "healthCheck.expectedResponse",
                        "rollbackProcedure", "securityHardening",
                        "hardwareRequirements.minimumCpu", "hardwareRequirements.minimumRam", "hardwareRequirements.minimumDisk",
                        "hardwareRequirements.recommendedCpu", "hardwareRequirements.recommendedRam", "hardwareRequirements.recommendedDisk"
                ),
                List.of(
                        new RepeatingGroup("component", "component", List.of("name", "role", "technology")),
                        new RepeatingGroup("swReq", "swReq", List.of("software", "minimumVersion", "purpose")),
                        new RepeatingGroup("envVar", "envVar", List.of("name", "description", "usedBy")),
                        new RepeatingGroup("port", "port", List.of("port", "protocol", "direction", "purpose")),
                        new RepeatingGroup("smokeTest", "smokeTest", List.of("step", "action", "expectedResult", "status")),
                        new RepeatingGroup("trouble", "trouble", List.of("symptom", "probableCause", "correctiveAction"))
                ),
                List.of());
    }

    private static DocumentSchema architectureDocument() {
        return new DocumentSchema(DocumentType.ARCHITECTURE_DOCUMENT, "templates/document/ArchitectureDocument_Template.docx",
                "Produce a system architecture document covering components, tech stack, security, and deployment.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction", "conclusion", "documentScope", "architecturalStyle", "componentInteractions",
                        "deploymentEnvironment", "containerizationStrategy", "pipelineDescription",
                        "securityModelOverview", "authenticationImplementation", "passwordSecurity", "transportSecurity",
                        "secretManagementIntro", "secretRotationPolicy", "additionalSecurityConsiderations",
                        "storageStrategyIntro"
                ),
                List.of(
                        new RepeatingGroup("goal", "goal", List.of("title", "points")),
                        new RepeatingGroup("principle", "principle", List.of("name", "description")),
                        new RepeatingGroup("module", "module", List.of("name", "responsibilities", "components")),
                        new RepeatingGroup("component", "component", List.of("name", "responsibility", "technology")),
                        new RepeatingGroup("entity", "entity", List.of("name", "attributes", "relationships")),
                        new RepeatingGroup("techStack", "techStack", List.of("stack", "technology", "role")),
                        new RepeatingGroup("infrastructureNode", "infrastructureNode", List.of("name", "technology", "purpose")),
                        new RepeatingGroup("storageType", "storageType", List.of("type", "technology", "dataStored")),
                        new RepeatingGroup("secret", "secret", List.of("name")),
                        new RepeatingGroup("lifecycleStage", "lifecycleStage", List.of("name", "description")),
                        new RepeatingGroup("workflowStep", "workflowStep", List.of("order", "description")),
                        new RepeatingGroup("decision", "decision", List.of("decision", "justification")),
                        new RepeatingGroup("architecturalView", "architecturalView", List.of("description"))
                ),
                List.of());
    }

    private static DocumentSchema apiContract() {
        return new DocumentSchema(DocumentType.API_CONTRACT, "templates/document/api-contract-template.docx",
                "Produce an API contract document covering general principles, endpoints, and traceability.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction", "apiSecurity", "webhookEvents",
                        "generalPrinciples.authentication", "generalPrinciples.pagination", "generalPrinciples.idempotency",
                        "generalPrinciples.errorFormat", "generalPrinciples.transport", "generalPrinciples.versioning",
                        "generalPrinciples.rateLimiting",
                        "coverageStats.totalEndpoints", "coverageStats.frCovered", "coverageStats.ucCovered",
                        "coverageStats.coveragePercent"
                ),
                List.of(
                        new RepeatingGroup("endpointGroup", "endpointGroup", List.of("name")),
                        new RepeatingGroup("endpoint", "endpoint", List.of("apiCode", "method", "path", "description")),
                        new RepeatingGroup("endpointDetail", "endpointDetail", List.of("apiCode", "method", "path",
                                "pathParameters", "queryParameters", "bodyParameters", "requiredHeaders",
                                "requestSchema", "responseSchema", "statusCodes", "frCovered", "security", "idempotent")),
                        new RepeatingGroup("httpCode", "httpCode", List.of("code", "name", "description")),
                        new RepeatingGroup("trace", "trace", List.of("apiCode", "method", "path", "frCovered", "ucCovered", "usCovered"))
                ),
                List.of());
    }

    private static DocumentSchema userManual() {
        return new DocumentSchema(DocumentType.USER_MANUAL, "templates/document/user-manual-template.docx",
                "Produce an end-user manual covering getting started, features, permissions, and FAQ.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version", "project.displayName",
                        "introduction.purpose", "introduction.scope", "introduction.intendedAudience", "introduction.howToUse",
                        "systemOverview.applicationPurpose", "systemOverview.accessUrl", "systemOverview.accessRequirements",
                        "gettingStarted.creatingAccount", "gettingStarted.loggingIn", "gettingStarted.loggingOut",
                        "gettingStarted.passwordReset", "gettingStarted.firstTimeSetup",
                        "interfaceOverview.mainNavigation", "interfaceOverview.dashboard", "interfaceOverview.commonElements"
                ),
                List.of(
                        new RepeatingGroup("featureModule", "featureModule", List.of("moduleName", "overview", "accessPath")),
                        new RepeatingGroup("moduleFeature", "moduleFeature", List.of("featureName", "steps", "expectedOutcome", "errorHandling")),
                        new RepeatingGroup("feature", "feature", List.of("area", "description")),
                        new RepeatingGroup("permission", "permission", List.of("action", "administrator", "manager", "standardUser")),
                        new RepeatingGroup("faq", "faq", List.of("question", "answer")),
                        new RepeatingGroup("glossary", "glossary", List.of("term", "definition")),
                        new RepeatingGroup("error", "error", List.of("message", "cause", "correctiveAction"))
                ),
                List.of());
    }

    private static DocumentSchema dataDictionary() {
        return new DocumentSchema(DocumentType.DATA_DICTIONARY, "templates/document/data-dictionary-template.docx",
                "Produce a data dictionary covering every persisted entity/table, its columns, and constraints.",
                scalars(
                        "project.name", "project.organisationName", "project.date", "project.version",
                        "introduction", "conclusion", "tableCount"
                ),
                List.of(
                        new RepeatingGroup("tbl", "tables", List.of("tableName", "description", "primaryKey")),
                        new RepeatingGroup("constraint", "constraints", List.of("tableName", "type", "detail"))
                ),
                List.of(new NestedGroupBlock("colTable", "col", "tables", "columns",
                        List.of("fieldName", "dataType", "fieldSize", "required", "description", "example"))));
    }
}
