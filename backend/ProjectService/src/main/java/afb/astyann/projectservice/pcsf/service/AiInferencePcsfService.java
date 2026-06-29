package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.client.AIServiceClient;
import afb.astyann.projectservice.domain.ClarificationQuestion;
import afb.astyann.projectservice.domain.PcsfStatus;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.dto.ProjectAnalysisResponseDTO;
import afb.astyann.projectservice.pcsf.dto.InferenceResponseDTO;
import afb.astyann.projectservice.pcsf.model.*;
import afb.astyann.projectservice.pcsf.model.enums.FieldSource;
import afb.astyann.projectservice.pcsf.model.enums.FieldStatus;
import afb.astyann.projectservice.pcsf.model.enums.RiskLevel;
import afb.astyann.projectservice.repository.ClarificationQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiInferencePcsfService {

    private final AIServiceClient                  aiServiceClient;
    private final ProjectRepository                projectRepository;
    private final ClarificationQuestionRepository  clarificationQuestionRepository;
    private final ObjectMapper                     objectMapper;

    @Async
    @Transactional
    public void runInferenceAsync(UUID projectId) {
        log.info("Starting PCSF AI inference for project={}", projectId);

        Project project = projectRepository.findByProjectId(projectId).orElse(null);
        if (project == null || project.getPcsfJson() == null) {
            log.error("Project or PCSF not found for inference: {}", projectId);
            return;
        }

        project.setPcsfStatus(PcsfStatus.INFERRING);
        projectRepository.save(project);

        try {
            Pcsf pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);

            // Merge document context with answered clarification questions before inference.
            // This gives every INF step the richest possible natural-language description.
            String fullContext = mergeContextWithAnswers(project);
            if (fullContext != null && !fullContext.isBlank()) {
                project.setProjectContext(fullContext);
                projectRepository.save(project);
            }

            runInf1Entities(pcsf, fullContext);
            runInf2Relationships(pcsf);
            runInf3BusinessRules(pcsf, fullContext);
            runInf4ScreensAndNfr(pcsf, fullContext);

            pcsf.getValidation().setPendingInferredItems(List.of());
            project.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            project.setPcsfStatus(PcsfStatus.UNDER_REVIEW);
            projectRepository.save(project);
            log.info("PCSF inference complete for project={} — status=UNDER_REVIEW", projectId);

        } catch (Exception ex) {
            log.error("PCSF inference failed for project={}", projectId, ex);
            project.setPcsfStatus(PcsfStatus.DRAFT);
            projectRepository.save(project);
        }
    }

    // ── INF-1: Entity Model ───────────────────────────────────────────────────

    private void runInf1Entities(Pcsf pcsf, String fullContext) {
        String projectName   = val(pcsf.getProject().getName());
        String description   = val(pcsf.getProject().getDescription());
        String moduleSummary = pcsf.getModules().stream()
                .map(m -> "- " + val(m.getName()) + ": " + val(m.getDescription()))
                .collect(Collectors.joining("\n"));
        String contextBlock  = fullContext != null && !fullContext.isBlank()
                ? "\nFULL PROJECT CONTEXT:\n" + fullContext + "\n" : "";

        String prompt = """
                You are a senior software architect.
                Based on the project context below, identify all database entities required.

                PROJECT NAME: %s
                DESCRIPTION: %s%s
                MODULES AND FEATURES:
                %s

                Apply these rules to EVERY entity:
                - Primary key: UUID (CHAR(36)), auto-generated
                - Always include audit fields: created_at (DATETIME), updated_at (DATETIME)
                - Do NOT list id, created_at, updated_at in the attributes array
                - Use snake_case for table/column names, PascalCase for entity class names, camelCase for attribute names

                Return ONLY valid JSON. No preamble.

                {
                  "entities": [
                    {
                      "name": "string",
                      "tableName": "string",
                      "primaryModuleId": "MOD-01",
                      "softDelete": false,
                      "attributes": [
                        {
                          "name": "string",
                          "columnName": "string",
                          "javaType": "String|Integer|Long|BigDecimal|Boolean|LocalDate|LocalDateTime",
                          "mysqlType": "VARCHAR(255)|INT|BIGINT|DECIMAL(10,2)|BOOLEAN|DATE|DATETIME|TEXT",
                          "required": true,
                          "unique": false,
                          "showInList": true,
                          "showInForm": true
                        }
                      ]
                    }
                  ]
                }
                """.formatted(projectName, description, contextBlock, moduleSummary);

        try {
            String content = infer("qwen2.5-coder:7b",
                    "You are a senior software architect. Return ONLY valid JSON.", prompt);
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode entitiesNode = root.path("entities");
            if (!entitiesNode.isArray()) return;

            List<PcsfEntity> entities = new ArrayList<>();
            AtomicInteger ei = new AtomicInteger(1);
            entitiesNode.forEach(e -> {
                String entId = String.format("ENT-%02d", ei.get());
                List<PcsfAttribute> attrs = new ArrayList<>();
                AtomicInteger ai = new AtomicInteger(1);
                e.path("attributes").forEach(a -> {
                    String attId = String.format("ATT-%02d-%02d", ei.get(), ai.getAndIncrement());
                    PcsfConstraints constraints = PcsfConstraints.builder()
                            .required(inferredBoolFv(a.path("required").asBoolean(false), 0.80))
                            .unique(inferredBoolFv(a.path("unique").asBoolean(false), 0.80))
                            .build();
                    attrs.add(PcsfAttribute.builder()
                            .id(attId)
                            .name(inferredFv(a.path("name").asText(null), 0.85))
                            .columnName(inferredFv(a.path("columnName").asText(null), 0.90))
                            .javaType(inferredFv(a.path("javaType").asText(null), 0.90))
                            .mysqlType(inferredFv(a.path("mysqlType").asText(null), 0.90))
                            .constraints(constraints)
                            .showInList(inferredBoolFv(a.path("showInList").asBoolean(true), 0.80))
                            .showInForm(inferredBoolFv(a.path("showInForm").asBoolean(true), 0.80))
                            .build());
                });
                entities.add(PcsfEntity.builder()
                        .id(entId)
                        .name(inferredFv(e.path("name").asText(null), 0.90))
                        .tableName(inferredFv(e.path("tableName").asText(null), 0.95))
                        .primaryModuleId(e.path("primaryModuleId").asText(null))
                        .softDelete(inferredBoolFv(e.path("softDelete").asBoolean(false), 0.80))
                        .attributes(attrs)
                        .build());
                ei.getAndIncrement();
            });
            pcsf.setEntities(entities);
            log.debug("INF-1 complete: {} entities", entities.size());
        } catch (Exception ex) {
            log.error("INF-1 entity inference failed", ex);
        }
    }

    // ── INF-2: Relationships ──────────────────────────────────────────────────

    private void runInf2Relationships(Pcsf pcsf) {
        if (pcsf.getEntities().isEmpty()) return;

        String entityList = pcsf.getEntities().stream()
                .map(e -> e.getId() + " - " + val(e.getName()) + " (" +
                        e.getAttributes().stream().map(a -> val(a.getName()))
                                .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("\n"));

        String prompt = """
                Based on the entities below, identify all relationships between them.

                ENTITIES:
                %s

                For each relationship:
                - fromEntityId, toEntityId: use entity IDs (e.g. ENT-01)
                - cardinality: ONE_TO_ONE | ONE_TO_MANY | MANY_TO_ONE | MANY_TO_MANY
                - optionality: MANDATORY | OPTIONAL
                - owningEntityId: entity that holds the FK column
                - joinColumnName: snake_case FK column name (null for MANY_TO_MANY)
                - joinTableName: join table name (only for MANY_TO_MANY)
                - label: plain English description

                Return ONLY valid JSON. No preamble.
                { "relationships": [{ "fromEntityId":"ENT-01","toEntityId":"ENT-02",
                  "cardinality":"ONE_TO_MANY","optionality":"MANDATORY",
                  "owningEntityId":"ENT-02","joinColumnName":"client_id",
                  "joinTableName":null,"label":"string" }] }
                """.formatted(entityList);

        try {
            String content = infer("qwen2.5-coder:7b",
                    "You are a senior software architect. Return ONLY valid JSON.", prompt);
            JsonNode root = objectMapper.readTree(cleanJson(content));
            List<PcsfRelationship> rels = new ArrayList<>();
            AtomicInteger ri = new AtomicInteger(1);
            root.path("relationships").forEach(r -> {
                rels.add(PcsfRelationship.builder()
                        .id(String.format("REL-%02d", ri.getAndIncrement()))
                        .fromEntityId(r.path("fromEntityId").asText(null))
                        .toEntityId(r.path("toEntityId").asText(null))
                        .cardinality(highRiskFv(r.path("cardinality").asText(null), 0.70))
                        .optionality(highRiskFv(r.path("optionality").asText(null), 0.70))
                        .owningEntityId(r.path("owningEntityId").asText(null))
                        .joinColumnName(r.path("joinColumnName").asText(null))
                        .joinTableName(r.path("joinTableName").asText(null))
                        .label(inferredFv(r.path("label").asText(null), 0.75))
                        .build());
            });
            pcsf.setRelationships(rels);
            log.debug("INF-2 complete: {} relationships", rels.size());
        } catch (Exception ex) {
            log.error("INF-2 relationship inference failed", ex);
        }
    }

    // ── INF-3: Business Rules, Status Machines, ACL ───────────────────────────

    private void runInf3BusinessRules(Pcsf pcsf, String fullContext) {
        String contextSummary = (fullContext != null && !fullContext.isBlank())
                ? fullContext : val(pcsf.getProject().getDescription());
        String entitySummary = pcsf.getEntities().stream()
                .map(e -> e.getId() + ":" + val(e.getName())).collect(Collectors.joining(", "));
        String actorSummary = pcsf.getActors().stream()
                .map(a -> val(a.getSpringSecurityRole()) + "(" + val(a.getName()) + ")")
                .collect(Collectors.joining(", "));
        String useCaseSummary = pcsf.getModules().stream()
                .flatMap(m -> (m.getUseCases() != null ? m.getUseCases() : List.<PcsfUseCase>of()).stream())
                .map(uc -> val(uc.getName()) + ": " + val(uc.getPreconditions()))
                .collect(Collectors.joining("; "));

        String prompt = """
                Based on the project context and entities below, produce:
                1. Business rules — constraints the system must enforce.
                2. Status machines — entities that have a lifecycle with named statuses.
                3. Access control — which role can perform which operation on which entity.

                PROJECT CONTEXT: %s
                ENTITIES: %s
                ACTORS/ROLES: %s
                USE CASES: %s

                Operations: CREATE, READ, READ_ALL, UPDATE, DELETE

                Return ONLY valid JSON:
                {
                  "businessRules": [
                    { "moduleId":"MOD-01","useCaseId":"UC-01","affectedEntityId":"ENT-01",
                      "description":"plain rule","implementationHint":"Spring Boot hint" }
                  ],
                  "statusMachines": [
                    { "entityId":"ENT-01","states":["PENDING","ACTIVE"],
                      "transitions":[{"from":"PENDING","to":"ACTIVE"}],"initialState":"PENDING" }
                  ],
                  "accessControlRules": [
                    { "moduleId":"MOD-01","entityId":"ENT-01","operation":"CREATE","allowedRoles":["ROLE_ADMIN"] }
                  ]
                }
                """.formatted(contextSummary, entitySummary, actorSummary, useCaseSummary);

        try {
            String content = infer("qwen2.5:7b",
                    "You are a senior Java architect. Return ONLY valid JSON.", prompt);
            JsonNode root = objectMapper.readTree(cleanJson(content));

            List<PcsfBusinessRule> brs = new ArrayList<>();
            AtomicInteger bi = new AtomicInteger(1);
            root.path("businessRules").forEach(br -> brs.add(PcsfBusinessRule.builder()
                    .id(String.format("BR-%02d", bi.getAndIncrement()))
                    .moduleId(br.path("moduleId").asText(null))
                    .useCaseId(br.path("useCaseId").asText(null))
                    .affectedEntityId(br.path("affectedEntityId").asText(null))
                    .description(highRiskFv(br.path("description").asText(null), 0.60))
                    .implementationHint(highRiskFv(br.path("implementationHint").asText(null), 0.60))
                    .build()));
            pcsf.setBusinessRules(brs);

            List<PcsfStatusMachine> sms = new ArrayList<>();
            root.path("statusMachines").forEach(sm -> {
                List<String> states = new ArrayList<>();
                sm.path("states").forEach(s -> states.add(s.asText()));
                List<PcsfStatusMachine.StatusTransition> transitions = new ArrayList<>();
                sm.path("transitions").forEach(t -> transitions.add(
                        PcsfStatusMachine.StatusTransition.builder()
                                .from(t.path("from").asText()).to(t.path("to").asText()).build()));
                sms.add(PcsfStatusMachine.builder()
                        .entityId(sm.path("entityId").asText(null))
                        .states(states).transitions(transitions)
                        .initialState(sm.path("initialState").asText(null)).build());
            });
            pcsf.setStatusMachines(sms);

            List<PcsfAccessControlRule> acls = new ArrayList<>();
            AtomicInteger ai = new AtomicInteger(1);
            root.path("accessControlRules").forEach(acl -> {
                List<String> roles = new ArrayList<>();
                acl.path("allowedRoles").forEach(r -> roles.add(r.asText()));
                acls.add(PcsfAccessControlRule.builder()
                        .id(String.format("ACL-%02d", ai.getAndIncrement()))
                        .moduleId(acl.path("moduleId").asText(null))
                        .entityId(acl.path("entityId").asText(null))
                        .operation(acl.path("operation").asText(null))
                        .allowedRoles(FieldValue.<List<String>>builder()
                                .value(roles).source(FieldSource.AI_INFERRED)
                                .status(FieldStatus.PENDING).confidence(0.75)
                                .riskLevel(RiskLevel.MEDIUM).build())
                        .build());
            });
            pcsf.setAccessControlRules(acls);
            log.debug("INF-3 complete: {} BRs, {} ACLs", brs.size(), acls.size());
        } catch (Exception ex) {
            log.error("INF-3 inference failed", ex);
        }
    }

    // ── INF-4: Screens, Navigation, NFRs ─────────────────────────────────────

    private void runInf4ScreensAndNfr(Pcsf pcsf, String fullContext) {
        String entitySummary = pcsf.getEntities().stream()
                .map(e -> e.getId() + ":" + val(e.getName())).collect(Collectors.joining(", "));
        String moduleSummary = pcsf.getModules().stream()
                .map(m -> m.getId() + ":" + val(m.getName())).collect(Collectors.joining(", "));
        String aclSummary = pcsf.getAccessControlRules().stream()
                .map(a -> a.getOperation() + " " + a.getEntityId() + ":" +
                        (a.getAllowedRoles() != null && a.getAllowedRoles().getValue() != null
                                ? a.getAllowedRoles().getValue().toString() : "[]"))
                .collect(Collectors.joining("; "));
        String contextBlock = fullContext != null && !fullContext.isBlank()
                ? "\nFULL PROJECT CONTEXT:\n" + fullContext + "\n" : "";

        String prompt = """
                Based on the entities, modules, access control rules and project context, generate:

                1. Angular screens — rules:
                   - Every entity gets one LIST screen and one FORM screen (create+edit)
                   - Every module without a direct entity gets one DASHBOARD screen
                   - Always include: Login, Register, ForgotPassword, ResetPassword, Dashboard
                   - Component names: PascalCase + "Component" suffix
                   - Route paths: kebab-case

                2. Sidebar navigation items — one per functional module.

                3. Standard non-functional requirements for a web application.

                ENTITIES: %s
                MODULES: %s
                ACCESS CONTROL: %s%s

                Return ONLY valid JSON:
                {
                  "screens": [
                    { "name":"UserListComponent","type":"LIST","entityId":"ENT-01",
                      "moduleId":"MOD-01","routePath":"/users","requiredRoles":["ROLE_ADMIN"],
                      "tableColumns":[{"attributeId":"ATT-01-01","headerLabel":"Name","sortable":true}],
                      "formFields":[{"attributeId":"ATT-01-01","label":"Name","controlType":"text"}] }
                  ],
                  "navigation": [
                    { "label":"User Mgmt","routePath":"/users","icon":"people",
                      "visibleToRoles":["ROLE_ADMIN"],"moduleId":"MOD-01" }
                  ],
                  "nfr": {
                    "concurrentUsers": 50,
                    "targetResponseTimeMs": 2000,
                    "dataVolumeDescription": "string",
                    "availabilityTarget": "99%%",
                    "securityDepth": "RBAC"
                  }
                }
                """.formatted(entitySummary, moduleSummary, aclSummary, contextBlock);

        try {
            String content = infer("qwen2.5-coder:7b",
                    "You are a senior Angular and Spring Boot architect. Return ONLY valid JSON.", prompt);
            JsonNode root = objectMapper.readTree(cleanJson(content));

            List<PcsfScreen> screens = new ArrayList<>();
            root.path("screens").forEach(s -> {
                List<String> roles = new ArrayList<>();
                s.path("requiredRoles").forEach(r -> roles.add(r.asText()));
                List<PcsfScreen.TableColumn> cols = new ArrayList<>();
                s.path("tableColumns").forEach(c -> cols.add(PcsfScreen.TableColumn.builder()
                        .attributeId(c.path("attributeId").asText(null))
                        .headerLabel(c.path("headerLabel").asText(null))
                        .sortable(c.path("sortable").asBoolean(false)).build()));
                List<PcsfScreen.FormField> fields = new ArrayList<>();
                s.path("formFields").forEach(f -> fields.add(PcsfScreen.FormField.builder()
                        .attributeId(f.path("attributeId").asText(null))
                        .label(f.path("label").asText(null))
                        .controlType(f.path("controlType").asText("text")).build()));
                screens.add(PcsfScreen.builder()
                        .name(s.path("name").asText(null))
                        .type(s.path("type").asText("LIST"))
                        .entityId(s.path("entityId").asText(null))
                        .moduleId(s.path("moduleId").asText(null))
                        .routePath(s.path("routePath").asText(null))
                        .requiredRoles(roles).tableColumns(cols).formFields(fields).build());
            });

            List<PcsfNavItem> nav = new ArrayList<>();
            root.path("navigation").forEach(n -> {
                List<String> roles = new ArrayList<>();
                n.path("visibleToRoles").forEach(r -> roles.add(r.asText()));
                nav.add(PcsfNavItem.builder()
                        .label(n.path("label").asText(null))
                        .routePath(n.path("routePath").asText(null))
                        .icon(n.path("icon").asText("circle"))
                        .moduleId(n.path("moduleId").asText(null))
                        .visibleToRoles(roles).build());
            });

            PcsfUserInterface ui = pcsf.getUserInterface() != null
                    ? pcsf.getUserInterface() : new PcsfUserInterface();
            ui.setScreens(screens);
            ui.setNavigation(nav);
            pcsf.setUserInterface(ui);

            JsonNode nfr = root.path("nfr");
            pcsf.setNonFunctionalRequirements(PcsfNonFunctionalRequirements.builder()
                    .concurrentUsers(inferredIntFv(nfr.path("concurrentUsers").asInt(50), 0.75))
                    .targetResponseTimeMs(inferredIntFv(nfr.path("targetResponseTimeMs").asInt(2000), 0.75))
                    .dataVolumeDescription(inferredFv(nfr.path("dataVolumeDescription").asText(null), 0.70))
                    .availabilityTarget(inferredFv(nfr.path("availabilityTarget").asText("99%"), 0.80))
                    .securityDepth(inferredFv(nfr.path("securityDepth").asText("RBAC"), 0.90))
                    .locale(FieldValue.<String>builder().value("en")
                            .source(FieldSource.DEFAULT).status(FieldStatus.CONFIRMED).build())
                    .build());
            log.debug("INF-4 complete: {} screens, {} nav items", screens.size(), nav.size());
        } catch (Exception ex) {
            log.error("INF-4 inference failed", ex);
        }
    }

    // ── Context Merge ─────────────────────────────────────────────────────────

    private String mergeContextWithAnswers(Project project) {
        List<ClarificationQuestion> answered = clarificationQuestionRepository
                .findByProject_ProjectIdOrderByPriorityAsc(project.getProjectId())
                .stream()
                .filter(q -> q.isAnswered() && q.getAnswer() != null && !q.getAnswer().isBlank())
                .toList();

        String existingContext = project.getProjectContext();

        if (answered.isEmpty()) {
            log.debug("No answered questions for project={} — using existing document context",
                    project.getProjectId());
            return existingContext;
        }

        List<AIServiceClient.MergeRequestBody.AnswerItem> answerItems = answered.stream()
                .map(q -> new AIServiceClient.MergeRequestBody.AnswerItem(q.getQuestion(), q.getAnswer()))
                .toList();

        try {
            ProjectAnalysisResponseDTO merged = aiServiceClient.mergeDocumentAndAnswers(
                    project.getProjectId(),
                    new AIServiceClient.MergeRequestBody(project.getProjectId(), existingContext, answerItems));

            if (merged != null && merged.getExtractedContext() != null
                    && !merged.getExtractedContext().isBlank()) {
                log.info("Context merged with {} Q&A pairs for project={}",
                        answered.size(), project.getProjectId());
                return merged.getExtractedContext();
            }
        } catch (Exception ex) {
            log.warn("Context merge failed for project={}, falling back to document context: {}",
                    project.getProjectId(), ex.getMessage());
        }

        return existingContext;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String infer(String model, String system, String user) {
        InferenceResponseDTO resp = aiServiceClient.infer(
                new AIServiceClient.InferBody(model, system, user));
        return resp != null ? resp.getContent() : "";
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("(?s)```json\\s*", "")
                  .replaceAll("(?s)```\\s*", "")
                  .trim();
    }

    private String val(FieldValue<?> fv) {
        return (fv != null && fv.getValue() != null) ? fv.getValue().toString() : "";
    }

    private FieldValue<String> inferredFv(String value, double confidence) {
        return FieldValue.<String>builder()
                .value(value).source(FieldSource.AI_INFERRED)
                .status(value != null ? FieldStatus.PENDING : FieldStatus.MISSING)
                .confidence(confidence).riskLevel(RiskLevel.LOW).build();
    }

    private FieldValue<String> highRiskFv(String value, double confidence) {
        return FieldValue.<String>builder()
                .value(value).source(FieldSource.AI_INFERRED)
                .status(value != null ? FieldStatus.PENDING : FieldStatus.MISSING)
                .confidence(confidence).riskLevel(RiskLevel.HIGH).build();
    }

    private FieldValue<Boolean> inferredBoolFv(boolean value, double confidence) {
        return FieldValue.<Boolean>builder()
                .value(value).source(FieldSource.AI_INFERRED)
                .status(FieldStatus.PENDING).confidence(confidence).riskLevel(RiskLevel.LOW).build();
    }

    private FieldValue<Integer> inferredIntFv(int value, double confidence) {
        return FieldValue.<Integer>builder()
                .value(value).source(FieldSource.AI_INFERRED)
                .status(FieldStatus.PENDING).confidence(confidence).riskLevel(RiskLevel.LOW).build();
    }
}
