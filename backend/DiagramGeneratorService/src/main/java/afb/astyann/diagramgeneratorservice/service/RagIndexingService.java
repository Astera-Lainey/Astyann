package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.client.RAGServiceClient;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import afb.astyann.diagramgeneratorservice.dto.RagIndexItemDTO;
import afb.astyann.diagramgeneratorservice.repository.UMLDiagramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIndexingService {

    private final UMLDiagramRepository repository;
    private final RAGServiceClient ragServiceClient;

    @Async("diagramExecutor")
    public void indexApprovedDiagramAsync(UUID diagramId) {
        try {
            UMLDiagram diagram = repository.findById(diagramId).orElse(null);
            if (diagram == null || diagram.getSourceCode() == null) {
                log.warn("Skipping RAG indexing for diagramId={} — diagram or source missing", diagramId);
                return;
            }
            ragServiceClient.indexDocument(RagIndexItemDTO.builder()
                    .projectId(diagram.getProjectId())
                    .sourceType("DIAGRAM")
                    .sourceId(diagram.getDiagramId())
                    .content(diagram.getSourceCode())
                    .metadata(Map.of("diagramType", diagram.getType().name()))
                    .build());
            log.info("RAG indexing completed for diagramId={}", diagramId);
        } catch (Exception ex) {
            // Indexing failure must never affect the APPROVED status — recovery is via RAGService's
            // own rebuild endpoint, same philosophy as RequirementService's approve() indexing.
            log.warn("RAG indexing failed for diagramId={}: {}", diagramId, ex.getMessage());
        }
    }
}
