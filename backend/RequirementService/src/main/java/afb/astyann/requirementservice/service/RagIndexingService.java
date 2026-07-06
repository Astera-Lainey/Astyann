package afb.astyann.requirementservice.service;

import afb.astyann.requirementservice.client.RAGServiceClient;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.dto.rag.RagBatchIndexDTO;
import afb.astyann.requirementservice.dto.rag.RagIndexItemDTO;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class RagIndexingService {

    private final RequirementRepository requirementRepository;
    private final ObjectMapper           objectMapper;
    private final RAGServiceClient       ragServiceClient;

    public RagIndexingService(RequirementRepository requirementRepository,
                              ObjectMapper objectMapper,
                              @Lazy RAGServiceClient ragServiceClient) {
        this.requirementRepository = requirementRepository;
        this.objectMapper          = objectMapper;
        this.ragServiceClient      = ragServiceClient;
    }

    @Async("pcsfExecutor")
    public void initializeIndexAsync(UUID requirementId) {
        log.info("RAG indexing started for requirementId={}", requirementId);
        try {
            Requirement req = requirementRepository.findById(requirementId).orElse(null);
            if (req == null || req.getPcsfJson() == null) {
                log.warn("RAG indexing skipped — requirement or PCSF not found for id={}", requirementId);
                return;
            }

            Pcsf pcsf = objectMapper.readValue(req.getPcsfJson(), Pcsf.class);
            List<RagIndexItemDTO> items = buildChunks(req, pcsf);

            if (items.isEmpty()) {
                log.warn("RAG indexing skipped — no indexable chunks for requirementId={}", requirementId);
                return;
            }

            RagBatchIndexDTO batch = RagBatchIndexDTO.builder()
                    .projectId(req.getProjectId())
                    .sourceType("REQUIREMENT")
                    .items(items)
                    .build();

            ragServiceClient.indexBatch(batch);
            log.info("RAG indexing completed for requirementId={} — {} chunks indexed",
                    requirementId, items.size());

        } catch (Exception ex) {
            // Graceful degradation: indexing failure must never affect the APPROVED status.
            // The index can be rebuilt via POST /api/v1/rag/{projectId}/rebuild on the RAG service.
            log.warn("RAG indexing failed for requirementId={} — will need manual rebuild: {}",
                    requirementId, ex.getMessage());
        }
    }

    private List<RagIndexItemDTO> buildChunks(Requirement req, Pcsf pcsf) {
        List<RagIndexItemDTO> items = new ArrayList<>();
        UUID projectId    = req.getProjectId();
        UUID requirementId = req.getRequirementId();

        addItem(items, projectId, requirementId, "project-overview",
                Map.of("title", req.getProjectTitle() != null ? req.getProjectTitle() : "",
                       "description", req.getProjectDescription() != null ? req.getProjectDescription() : ""),
                buildProjectOverview(req, pcsf));

        if (pcsf.getActors() != null && !pcsf.getActors().isEmpty()) {
            addItem(items, projectId, requirementId, "actors", Map.of(), pcsf.getActors());
        }

        if (pcsf.getModules() != null) {
            for (var module : pcsf.getModules()) {
                String moduleId = module.getId() != null ? module.getId() : "unknown";
                addItem(items, projectId, requirementId, "module-" + moduleId,
                        Map.of("moduleId", moduleId), module);
            }
        }

        if (pcsf.getEntities() != null && !pcsf.getEntities().isEmpty()) {
            addItem(items, projectId, requirementId, "entities", Map.of(), pcsf.getEntities());
        }

        if (pcsf.getRelationships() != null && !pcsf.getRelationships().isEmpty()) {
            addItem(items, projectId, requirementId, "relationships", Map.of(), pcsf.getRelationships());
        }

        if (pcsf.getBusinessRules() != null && !pcsf.getBusinessRules().isEmpty()) {
            addItem(items, projectId, requirementId, "business-rules", Map.of(), pcsf.getBusinessRules());
        }

        if (pcsf.getStatusMachines() != null && !pcsf.getStatusMachines().isEmpty()) {
            addItem(items, projectId, requirementId, "status-machines", Map.of(), pcsf.getStatusMachines());
        }

        if (pcsf.getAccessControlRules() != null && !pcsf.getAccessControlRules().isEmpty()) {
            addItem(items, projectId, requirementId, "access-control", Map.of(), pcsf.getAccessControlRules());
        }

        if (pcsf.getErrorCodes() != null && !pcsf.getErrorCodes().isEmpty()) {
            addItem(items, projectId, requirementId, "error-codes", Map.of(), pcsf.getErrorCodes());
        }

        if (pcsf.getEndpoints() != null && !pcsf.getEndpoints().isEmpty()) {
            addItem(items, projectId, requirementId, "api-endpoints", Map.of(), pcsf.getEndpoints());
        }

        if (pcsf.getNonFunctionalRequirements() != null) {
            addItem(items, projectId, requirementId, "nfr", Map.of(), pcsf.getNonFunctionalRequirements());
        }

        if (pcsf.getUserInterface() != null) {
            var ui = pcsf.getUserInterface();
            if (ui.getScreens() != null && !ui.getScreens().isEmpty()) {
                addItem(items, projectId, requirementId, "ui-screens", Map.of(), ui.getScreens());
            }
            if (ui.getNavigation() != null && !ui.getNavigation().isEmpty()) {
                addItem(items, projectId, requirementId, "navigation", Map.of(), ui.getNavigation());
            }
            if (ui.getColours() != null) {
                addItem(items, projectId, requirementId, "ui-colours", Map.of(), ui.getColours());
            }
        }

        // Infrastructure is grouped in one chunk to keep downstream context retrieval simple
        var infraPayload = new java.util.LinkedHashMap<String, Object>();
        if (pcsf.getApiConfig()            != null) infraPayload.put("apiConfig",            pcsf.getApiConfig());
        if (pcsf.getDatabaseConfig()       != null) infraPayload.put("databaseConfig",       pcsf.getDatabaseConfig());
        if (pcsf.getInfrastructureConfig() != null) infraPayload.put("infrastructureConfig", pcsf.getInfrastructureConfig());
        if (!infraPayload.isEmpty()) {
            addItem(items, projectId, requirementId, "infrastructure", Map.of(), infraPayload);
        }

        return items;
    }

    private Object buildProjectOverview(Requirement req, Pcsf pcsf) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("projectTitle",       req.getProjectTitle());
        map.put("projectDescription", req.getProjectDescription());
        if (pcsf.getProject() != null) map.put("project", pcsf.getProject());
        if (pcsf.getPublicAccess() != null) map.put("publicAccess", pcsf.getPublicAccess());
        if (pcsf.getConditionalFeatures() != null) map.put("conditionalFeatures", pcsf.getConditionalFeatures());
        return map;
    }

    private void addItem(List<RagIndexItemDTO> items, UUID projectId, UUID requirementId,
                         String section, Map<String, String> extraMeta, Object payload) {
        try {
            String content = objectMapper.writeValueAsString(payload);
            var metadata = new java.util.HashMap<>(extraMeta);
            metadata.put("section",       section);
            metadata.put("projectId",     projectId.toString());
            metadata.put("requirementId", requirementId.toString());

            items.add(RagIndexItemDTO.builder()
                    .projectId(projectId)
                    .sourceType("REQUIREMENT")
                    .sourceId(requirementId)
                    .content(content)
                    .metadata(metadata)
                    .build());
        } catch (JsonProcessingException ex) {
            log.warn("Skipping RAG chunk '{}' — serialization failed: {}", section, ex.getMessage());
        }
    }
}
