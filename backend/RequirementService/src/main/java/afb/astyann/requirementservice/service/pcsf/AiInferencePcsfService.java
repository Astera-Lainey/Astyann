package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.client.AIServiceClient;
import afb.astyann.requirementservice.domain.ClarificationQuestion;
import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.dto.InferenceResponseDTO;
import afb.astyann.requirementservice.repository.ClarificationQuestionRepository;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiInferencePcsfService {

    private static final String INF1_MODEL = "qwen2.5-coder:7b";
    private static final String INF3_MODEL = "qwen2.5-coder:3b";
    private static final String INF4_MODEL = "qwen2.5-coder:3b";

    private static final String INF1_SYSTEM = """
            You are a senior software architect specializing in enterprise systems for African banks.
            Analyse the project context provided and extract all entities, relationships, and
            business rules. Return ONLY valid JSON. No preamble. No explanation.
            """;

    private static final String INF3_SYSTEM = """
            You are a UX analyst for Afriland First Bank web applications.
            Based on the project context and use-cases, define the screen hierarchy,
            navigation flows, and UI components. Return ONLY valid JSON. No preamble.
            """;

    private static final String INF4_SYSTEM = """
            You are an API architect for Afriland First Bank microservices.
            Based on the entities and use-cases, define the REST API endpoints,
            database schema, and non-functional requirements. Return ONLY valid JSON. No preamble.
            """;

    private final RequirementRepository           requirementRepository;
    private final ClarificationQuestionRepository questionRepository;
    private final AIServiceClient                  aiServiceClient;
    private final ObjectMapper                     objectMapper;

    @Async("pcsfExecutor")
    public void runInferenceAsync(UUID requirementId) {
        Requirement requirement = requirementRepository.findById(requirementId).orElse(null);
        if (requirement == null) {
            log.error("Requirement not found for inference: {}", requirementId);
            return;
        }

        log.info("Starting AI inference pipeline for requirement={}", requirementId);
        requirement.setPcsfStatus(PcsfStatus.INFERRING);
        requirementRepository.save(requirement);

        try {
            String fullContext = mergeContextWithAnswers(requirement);

            runINF1(requirement, fullContext);
            runINF3(requirement, fullContext);
            runINF4(requirement, fullContext);

            requirement.setPcsfStatus(PcsfStatus.UNDER_REVIEW);
            requirementRepository.save(requirement);
            log.info("AI inference pipeline completed for requirement={}", requirementId);

        } catch (Exception ex) {
            log.error("AI inference pipeline failed for requirement={}", requirementId, ex);
            requirement.setPcsfStatus(PcsfStatus.FAILED);
            requirementRepository.save(requirement);
        }
    }

    @Async("pcsfExecutor")
    public void runReInferenceAsync(UUID requirementId, String changeInstructions) {
        Requirement requirement = requirementRepository.findById(requirementId).orElse(null);
        if (requirement == null) {
            log.error("Requirement not found for re-inference: {}", requirementId);
            return;
        }

        log.info("Starting re-inference pipeline for requirement={} with change instructions", requirementId);
        requirement.setPcsfStatus(PcsfStatus.INFERRING);
        requirementRepository.save(requirement);

        try {
            String baseContext = mergeContextWithAnswers(requirement);
            String fullContext = baseContext + "\n\n--- CHANGE INSTRUCTIONS ---\n" + changeInstructions;

            runINF1(requirement, fullContext);
            runINF3(requirement, fullContext);
            runINF4(requirement, fullContext);

            requirement.setPcsfStatus(PcsfStatus.UNDER_REVIEW);
            requirement.setChangeInstructions(null);
            requirementRepository.save(requirement);
            log.info("Re-inference pipeline completed for requirement={}", requirementId);

        } catch (Exception ex) {
            log.error("Re-inference pipeline failed for requirement={}", requirementId, ex);
            requirement.setPcsfStatus(PcsfStatus.FAILED);
            requirementRepository.save(requirement);
        }
    }

    private String mergeContextWithAnswers(Requirement requirement) {
        List<ClarificationQuestion> answered = questionRepository
                .findByRequirement_RequirementIdOrderByPriorityAsc(requirement.getRequirementId());

        String projectContext = requirement.getProjectContext() != null
                ? requirement.getProjectContext() : "";

        if (answered.isEmpty()) return projectContext;

        StringBuilder sb = new StringBuilder(projectContext);
        sb.append("\n\n--- CLARIFICATION Q&A ---\n");
        answered.stream()
                .filter(q -> q.getAnswer() != null && !q.getAnswer().isBlank())
                .forEach(q -> sb.append("Q: ").append(q.getQuestion())
                                .append("\nA: ").append(q.getAnswer()).append("\n\n"));
        return sb.toString();
    }

    private void runINF1(Requirement requirement, String context) throws Exception {
        String userPrompt = "Project context:\n" + context + """

                Extract all entities, relationships, business rules, status machines, access control rules, and error codes.
                Return ONLY valid JSON matching this exact schema — use these exact field names:
                {
                  "entities": [
                    {
                      "id": "entity_1",
                      "name": "EntityName",
                      "tableName": "table_name",
                      "attributes": [
                        {"id": "attr_1", "name": "attributeName", "javaType": "String",
                         "mysqlType": "VARCHAR(255)", "columnName": "column_name",
                         "constraints": {"required": true, "unique": false, "minLength": 2, "maxLength": 100}}
                      ]
                    }
                  ],
                  "relationships": [
                    {
                      "id": "rel_1",
                      "fromEntityId": "entity_1",
                      "toEntityId": "entity_2",
                      "cardinality": "ONE_TO_MANY",
                      "label": "owns",
                      "owningEntityId": "entity_1",
                      "joinColumnName": "entity1_id"
                    }
                  ],
                  "businessRules": [
                    {
                      "id": "rule_1",
                      "description": "rule text",
                      "implementationHint": "how to implement",
                      "affectedEntityId": "entity_1",
                      "moduleId": "module_1"
                    }
                  ],
                  "statusMachines": [
                    {
                      "entityId": "entity_1",
                      "states": ["PENDING", "ACTIVE", "CLOSED"],
                      "initialState": "PENDING",
                      "transitions": [
                        {"from": "PENDING", "to": "ACTIVE", "trigger": "activate",
                         "guard": "balance > 0", "action": "sendActivationEmail()"}
                      ]
                    }
                  ],
                  "accessControlRules": [
                    {"id": "acl_1", "moduleId": "module_1", "entityId": "entity_1",
                     "operation": "CREATE", "allowedRoles": ["ADMIN", "MANAGER"]}
                  ],
                  "errorCodes": [
                    {
                      "id": "err_1",
                      "code": "ENTITY_NOT_FOUND",
                      "httpStatus": 404,
                      "messageTemplate": "Entity with id {id} not found",
                      "exceptionClass": "EntityNotFoundException",
                      "moduleId": "module_1"
                    }
                  ]
                }
                Use exact field names. No preamble. No code fences. No explanation.""";

        InferenceResponseDTO response = aiServiceClient.infer(
                new AIServiceClient.InferBody(INF1_MODEL, INF1_SYSTEM, userPrompt));

        if (response == null || response.getContent() == null) {
            log.warn("INF-1 returned null for requirement={}", requirement.getRequirementId());
            return;
        }

        applyInf1Result(requirement, cleanJson(response.getContent()));
    }

    private void applyInf1Result(Requirement requirement, String json) throws Exception {
        Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
        var node = objectMapper.readTree(json);

        if (node.has("entities") && node.get("entities").isArray()) {
            var entities = objectMapper.convertValue(node.get("entities"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfEntity.class));
            pcsf.setEntities((List<PcsfEntity>) entities);
        }
        if (node.has("relationships") && node.get("relationships").isArray()) {
            var rels = objectMapper.convertValue(node.get("relationships"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfRelationship.class));
            pcsf.setRelationships((List<PcsfRelationship>) rels);
        }
        if (node.has("businessRules") && node.get("businessRules").isArray()) {
            var rules = objectMapper.convertValue(node.get("businessRules"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfBusinessRule.class));
            pcsf.setBusinessRules((List<PcsfBusinessRule>) rules);
        }
        if (node.has("statusMachines") && node.get("statusMachines").isArray()) {
            var machines = objectMapper.convertValue(node.get("statusMachines"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfStatusMachine.class));
            pcsf.setStatusMachines((List<PcsfStatusMachine>) machines);
        }
        if (node.has("accessControlRules") && node.get("accessControlRules").isArray()) {
            var acls = objectMapper.convertValue(node.get("accessControlRules"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfAccessControlRule.class));
            pcsf.setAccessControlRules((List<PcsfAccessControlRule>) acls);
        }
        if (node.has("errorCodes") && node.get("errorCodes").isArray()) {
            var errors = objectMapper.convertValue(node.get("errorCodes"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfErrorCode.class));
            pcsf.setErrorCodes((List<PcsfErrorCode>) errors);
        }

        requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        requirementRepository.save(requirement);
        log.debug("INF-1 applied for requirement={}", requirement.getRequirementId());
    }

    private void runINF3(Requirement requirement, String context) throws Exception {
        String pcsfSummary = buildPcsfSummary(requirement);
        String userPrompt = "Project context:\n" + context
                + "\n\nCurrent PCSF summary:\n" + pcsfSummary + """

                Define the screen hierarchy and navigation for this application.
                Return ONLY valid JSON matching this exact schema — use these exact field names:
                {
                  "screens": [
                    {
                      "name": "ScreenName",
                      "type": "LIST",
                      "entityId": "entity_1",
                      "moduleId": "module_1",
                      "routePath": "/path",
                      "requiredRoles": ["ADMIN"],
                      "tableColumns": [{"attributeId": "attr_1", "headerLabel": "Header", "sortable": true}],
                      "formFields": [{"attributeId": "attr_1", "label": "Field Label", "controlType": "INPUT"}]
                    }
                  ],
                  "navItems": [
                    {
                      "label": "Nav Label",
                      "routePath": "/path",
                      "icon": "icon-name",
                      "visibleToRoles": ["ADMIN"],
                      "moduleId": "module_1"
                    }
                  ]
                }
                Use exact field names. No preamble. No code fences. No explanation.""";

        InferenceResponseDTO response = aiServiceClient.infer(
                new AIServiceClient.InferBody(INF3_MODEL, INF3_SYSTEM, userPrompt));

        if (response == null || response.getContent() == null) {
            log.warn("INF-3 returned null for requirement={}", requirement.getRequirementId());
            return;
        }

        applyInf3Result(requirement, cleanJson(response.getContent()));
    }

    private void applyInf3Result(Requirement requirement, String json) throws Exception {
        Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
        var node = objectMapper.readTree(json);

        if (pcsf.getUserInterface() == null)
            pcsf.setUserInterface(new afb.astyann.requirementservice.domain.pcsf.PcsfUserInterface());

        if (node.has("screens") && node.get("screens").isArray()) {
            var screens = objectMapper.convertValue(node.get("screens"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfScreen.class));
            pcsf.getUserInterface().setScreens((List<PcsfScreen>) screens);
        }
        if (node.has("navItems") && node.get("navItems").isArray()) {
            var navItems = objectMapper.convertValue(node.get("navItems"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfNavItem.class));
            pcsf.getUserInterface().setNavigation((List<PcsfNavItem>) navItems);
        }

        requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        requirementRepository.save(requirement);
        log.debug("INF-3 applied for requirement={}", requirement.getRequirementId());
    }

    private void runINF4(Requirement requirement, String context) throws Exception {
        String pcsfSummary = buildPcsfSummary(requirement);
        String userPrompt = "Project context:\n" + context
                + "\n\nCurrent PCSF summary:\n" + pcsfSummary + """

                Define REST API endpoints, API config, database config, non-functional requirements,
                and infrastructure config.
                Return ONLY valid JSON matching this exact schema — use these exact field names:
                {
                  "endpoints": [
                    {
                      "id": "ep_1",
                      "moduleId": "module_1",
                      "httpMethod": "GET",
                      "path": "/api/v1/loans",
                      "operationId": "getAllLoans",
                      "summary": "Retrieve paginated list of loans",
                      "requestBodyEntityId": null,
                      "responseEntityId": "entity_1",
                      "requiredRoles": ["LOAN_OFFICER"],
                      "paginated": true,
                      "requiresAuth": true
                    }
                  ],
                  "apiConfig": {
                    "versionPrefix": "/api/v1",
                    "rateLimitPerMinute": 1000,
                    "corsAllowedOriginsDev": "http://localhost:4200",
                    "defaultPageSize": 20,
                    "maxPageSize": 100
                  },
                  "databaseConfig": {
                    "name": "db_name",
                    "user": "db_user"
                  },
                  "nonFunctionalRequirements": {
                    "concurrentUsers": 100,
                    "targetResponseTimeMs": 500,
                    "dataVolumeDescription": "e.g. 50 000 transactions/day",
                    "availabilityTarget": "99.9%",
                    "securityDepth": "BANK_GRADE",
                    "locale": "fr-CM"
                  },
                  "infrastructureConfig": {
                    "backendPort": 8080,
                    "frontendPort": 4200,
                    "diagramRenderer": "NONE",
                    "krokiInternalUrl": null,
                    "deploymentTarget": "VPS"
                  }
                }
                Generate one endpoint per use-case derived operation (CRUD + custom actions).
                For infrastructureConfig:
                - deploymentTarget must be one of VPS, DOCKER_COMPOSE, KUBERNETES — pick based on the
                  concurrentUsers/availabilityTarget you just set (small internal tool with modest load →
                  VPS or DOCKER_COMPOSE; high-concurrency or high-availability need → KUBERNETES).
                - diagramRenderer is "NONE" by default. Kroki is an external diagram-rendering dependency —
                  only set it to "kroki" (and fill krokiInternalUrl as "http://kroki:8000") if the
                  application's own use-cases require rendering diagrams/flowcharts/org-charts to end
                  users as a product feature. Do not set it just because the project has entities or
                  workflows — most business applications do not need it.
                Use exact field names. No preamble. No code fences. No explanation.""";

        InferenceResponseDTO response = aiServiceClient.infer(
                new AIServiceClient.InferBody(INF4_MODEL, INF4_SYSTEM, userPrompt));

        if (response == null || response.getContent() == null) {
            log.warn("INF-4 returned null for requirement={}", requirement.getRequirementId());
            return;
        }

        applyInf4Result(requirement, cleanJson(response.getContent()));
    }

    private void applyInf4Result(Requirement requirement, String json) throws Exception {
        Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
        var node = objectMapper.readTree(json);

        if (node.has("endpoints") && node.get("endpoints").isArray()) {
            var eps = objectMapper.convertValue(node.get("endpoints"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                    afb.astyann.requirementservice.domain.pcsf.PcsfApiEndpoint.class));
            pcsf.setEndpoints((List<PcsfApiEndpoint>) eps);
        }
        if (node.has("apiConfig")) {
            var apiConfig = objectMapper.treeToValue(node.get("apiConfig"),
                    afb.astyann.requirementservice.domain.pcsf.PcsfApiConfig.class);
            pcsf.setApiConfig(apiConfig);
        }
        if (node.has("databaseConfig")) {
            var dbConfig = objectMapper.treeToValue(node.get("databaseConfig"),
                    afb.astyann.requirementservice.domain.pcsf.PcsfDatabaseConfig.class);
            if (pcsf.getDatabaseConfig() != null) {
                if (dbConfig.getName() != null) pcsf.getDatabaseConfig().setName(dbConfig.getName());
                if (dbConfig.getUser() != null) pcsf.getDatabaseConfig().setUser(dbConfig.getUser());
            } else {
                pcsf.setDatabaseConfig(dbConfig);
            }
        }
        if (node.has("nonFunctionalRequirements")) {
            var nfr = objectMapper.treeToValue(node.get("nonFunctionalRequirements"),
                    afb.astyann.requirementservice.domain.pcsf.PcsfNonFunctionalRequirements.class);
            pcsf.setNonFunctionalRequirements(nfr);
        }
        if (node.has("infrastructureConfig")) {
            var infraConfig = objectMapper.treeToValue(node.get("infrastructureConfig"),
                    afb.astyann.requirementservice.domain.pcsf.PcsfInfrastructureConfig.class);
            pcsf.setInfrastructureConfig(infraConfig);
        }

        requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        requirementRepository.save(requirement);
        log.debug("INF-4 applied for requirement={}", requirement.getRequirementId());
    }

    private String buildPcsfSummary(Requirement requirement) {
        try {
            Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
            StringBuilder sb = new StringBuilder();
            if (pcsf.getProject() != null && pcsf.getProject().getName() != null
                    && pcsf.getProject().getName().getValue() != null)
                sb.append("Project: ").append(pcsf.getProject().getName().getValue()).append("\n");
            if (pcsf.getActors() != null)
                pcsf.getActors().forEach(a -> {
                    if (a.getName() != null && a.getName().getValue() != null)
                        sb.append("Actor: ").append(a.getName().getValue()).append("\n");
                });
            if (pcsf.getModules() != null)
                pcsf.getModules().forEach(m -> {
                    if (m.getName() != null && m.getName().getValue() != null)
                        sb.append("Module: ").append(m.getName().getValue()).append("\n");
                    if (m.getUseCases() != null)
                        m.getUseCases().forEach(uc -> {
                            if (uc.getName() != null && uc.getName().getValue() != null)
                                sb.append("  UC: ").append(uc.getName().getValue()).append("\n");
                        });
                });
            return sb.toString();
        } catch (Exception ex) {
            return "PCSF summary unavailable";
        }
    }

    private String cleanJson(String raw) {
        return raw.replaceAll("(?s)```json\\s*", "")
                  .replaceAll("(?s)```\\s*", "")
                  .trim();
    }
}
