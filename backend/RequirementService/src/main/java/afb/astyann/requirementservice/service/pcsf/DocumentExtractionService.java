package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.client.AIServiceClient;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import afb.astyann.requirementservice.dto.InferenceResponseDTO;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentExtractionService {

    private static final String SYSTEM_PROMPT = """
            You are a requirements analyst working for Afriland First Bank.
            Extract information from the project specification document and return it as JSON.
            Return ONLY valid JSON. No preamble. No explanation.
            If a field cannot be found in the document, set its value to null.
            """;

    private final AIServiceClient          aiServiceClient;
    private final RequirementRepository    requirementRepository;
    private final ObjectMapper             objectMapper;
    private final PcsfCompletenessAnalyser completenessAnalyser;

    @Transactional
    public void extract(Requirement requirement, String documentText) {
        if (documentText == null || documentText.isBlank()) {
            log.warn("Empty document text for requirement={}, skipping extraction",
                    requirement.getRequirementId());
            completenessAnalyser.analyse(requirement);
            return;
        }

        log.info("Extracting PCSF fields from document for requirement={}", requirement.getRequirementId());

        String userPrompt = buildExtractionPrompt(documentText);

        try {
            InferenceResponseDTO response = aiServiceClient.infer(
                    new AIServiceClient.InferBody("qwen2.5-coder:7b", SYSTEM_PROMPT, userPrompt));

            if (response == null || response.getContent() == null) {
                log.warn("Null inference response for requirement={}", requirement.getRequirementId());
                completenessAnalyser.analyse(requirement);
                return;
            }

            applyExtractionResult(requirement, response.getContent());

        } catch (Exception ex) {
            log.error("Document extraction failed for requirement={}", requirement.getRequirementId(), ex);
        }

        completenessAnalyser.analyse(requirement);
    }

    private String buildExtractionPrompt(String documentText) {
        return """
                Extract information from the project specification document below.

                DOCUMENT TEXT:
                """ + documentText + """

                Extract the following fields if present. Return ONLY valid JSON.
                If a field cannot be found in the document, set its value to null.

                OUTPUT SCHEMA:
                {
                  "projectName": "string | null",
                  "projectDescription": "string | null",
                  "displayName": "string | null",
                  "actors": [
                    { "name": "string", "type": "INTERNAL | EXTERNAL", "description": "string" }
                  ],
                  "modules": [
                    {
                      "name": "string",
                      "description": "string",
                      "crudOperations": ["CREATE","READ","UPDATE","DELETE"],
                      "useCases": [
                        {
                          "name": "string",
                          "actorName": "string",
                          "preconditions": "string",
                          "postconditions": "string",
                          "mainScenario": ["step 1", "step 2"],
                          "alternativeScenario": "string"
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private void applyExtractionResult(Requirement requirement, String rawContent) throws Exception {
        String json = cleanJson(rawContent);
        Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);

        JsonNode root = objectMapper.readTree(json);

        applyStringField(pcsf.getProject().getName(),        root, "projectName");
        applyStringField(pcsf.getProject().getDescription(), root, "projectDescription");
        applyStringField(pcsf.getProject().getDisplayName(), root, "displayName");

        JsonNode actorsNode = root.path("actors");
        if (actorsNode.isArray() && actorsNode.size() > 0) {
            List<PcsfActor> actors = new ArrayList<>();
            AtomicInteger i = new AtomicInteger(1);
            actorsNode.forEach(a -> {
                String type = a.path("type").asText("INTERNAL");
                actors.add(PcsfActor.builder()
                        .id(String.format("ACT-%02d", i.getAndIncrement()))
                        .name(extractedFv(a.path("name").asText(null)))
                        .type(extractedFv(type.toUpperCase().contains("EXTERNAL") ? "EXTERNAL" : "INTERNAL"))
                        .description(extractedFv(a.path("description").asText(null)))
                        .build());
            });
            pcsf.setActors(actors);
        }

        JsonNode modulesNode = root.path("modules");
        if (modulesNode.isArray() && modulesNode.size() > 0) {
            List<PcsfModule> modules = new ArrayList<>();
            AtomicInteger mi = new AtomicInteger(1);
            AtomicInteger ui = new AtomicInteger(1);
            modulesNode.forEach(m -> {
                List<String> crud = new ArrayList<>();
                m.path("crudOperations").forEach(op -> crud.add(op.asText()));
                if (crud.isEmpty()) crud.addAll(List.of("CREATE", "READ", "UPDATE", "DELETE"));

                List<PcsfUseCase> useCases = new ArrayList<>();
                m.path("useCases").forEach(uc -> {
                    List<String> scenario = new ArrayList<>();
                    uc.path("mainScenario").forEach(s -> scenario.add(s.asText()));
                    useCases.add(PcsfUseCase.builder()
                            .id(String.format("UC-%02d", ui.getAndIncrement()))
                            .name(extractedFv(uc.path("name").asText(null)))
                            .preconditions(extractedFv(uc.path("preconditions").asText(null)))
                            .postconditions(extractedFv(uc.path("postconditions").asText(null)))
                            .mainScenario(FieldValue.<List<String>>builder()
                                    .value(scenario.isEmpty() ? null : scenario)
                                    .source(FieldSource.EXTRACTED)
                                    .status(scenario.isEmpty() ? FieldStatus.MISSING : FieldStatus.CONFIRMED)
                                    .build())
                            .alternativeScenario(extractedFv(uc.path("alternativeScenario").asText(null)))
                            .build());
                });

                modules.add(PcsfModule.builder()
                        .id(String.format("MOD-%02d", mi.getAndIncrement()))
                        .name(extractedFv(m.path("name").asText(null)))
                        .description(extractedFv(m.path("description").asText(null)))
                        .crudOperations(FieldValue.<List<String>>builder()
                                .value(crud)
                                .source(FieldSource.EXTRACTED)
                                .status(FieldStatus.CONFIRMED)
                                .build())
                        .useCases(useCases)
                        .build());
            });
            pcsf.setModules(modules);
        }

        requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        requirementRepository.save(requirement);
        log.debug("Extraction applied and PCSF saved for requirement={}", requirement.getRequirementId());
    }

    private void applyStringField(FieldValue<String> field, JsonNode root, String jsonKey) {
        if (field == null) return;
        String val = root.path(jsonKey).asText(null);
        if (val != null && !val.equals("null") && !val.isBlank()) {
            field.setValue(val);
            field.setSource(FieldSource.EXTRACTED);
            field.setStatus(FieldStatus.CONFIRMED);
        } else {
            field.setStatus(FieldStatus.MISSING);
        }
    }

    private FieldValue<String> extractedFv(String value) {
        if (value == null || value.equals("null") || value.isBlank()) {
            return FieldValue.<String>builder().status(FieldStatus.MISSING).build();
        }
        return FieldValue.<String>builder()
                .value(value).source(FieldSource.EXTRACTED).status(FieldStatus.CONFIRMED).build();
    }

    private String cleanJson(String raw) {
        return raw.replaceAll("(?s)```json\\s*", "")
                  .replaceAll("(?s)```\\s*", "")
                  .trim();
    }
}
