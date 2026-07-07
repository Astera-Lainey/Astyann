package afb.astyann.ragservice.controller;

import afb.astyann.ragservice.dto.BatchIndexRequestDTO;
import afb.astyann.ragservice.dto.IndexRequestDTO;
import afb.astyann.ragservice.dto.RetrievalResponseDTO;
import afb.astyann.ragservice.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
public class RAGController {

    private final RAGService ragService;

    @PostMapping("/index")
    public ResponseEntity<Void> indexDocument(@RequestBody IndexRequestDTO dto) {
        ragService.indexDocument(dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/index/batch")
    public ResponseEntity<Void> indexBatch(@RequestBody BatchIndexRequestDTO dto) {
        ragService.indexBatch(dto);
        return ResponseEntity.noContent().build();
    }

    /** Returns plain concatenated context string — matches AIOrchestrator RAGServiceClient */
    @GetMapping("/context")
    public ResponseEntity<String> getContext(
            @RequestParam UUID projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        RetrievalResponseDTO response = ragService.retrieveContext(projectId, query, topK, null);
        return ResponseEntity.ok(response.getContext());
    }

    /** Returns list of source section names — matches AIOrchestrator RAGServiceClient */
    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources(
            @RequestParam UUID projectId,
            @RequestParam String query) {
        RetrievalResponseDTO response = ragService.retrieveContext(projectId, query, 10, null);
        return ResponseEntity.ok(response.getSources());
    }

    /** Full retrieval response with chunks, sources, and scores */
    @GetMapping("/retrieve")
    public ResponseEntity<RetrievalResponseDTO> retrieve(
            @RequestParam UUID projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String sourceTypeFilter) {
        return ResponseEntity.ok(ragService.retrieveContext(projectId, query, topK, sourceTypeFilter));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteIndex(@PathVariable UUID projectId) {
        ragService.deleteIndex(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/rebuild")
    public ResponseEntity<Map<String, String>> rebuildIndex(@PathVariable UUID projectId) {
        ragService.rebuildIndex(projectId);
        return ResponseEntity.ok(Map.of(
                "status",  "INDEX_CLEARED",
                "message", "Index cleared for project " + projectId +
                           ". Re-trigger via POST /api/v1/requirements/" + projectId + "/approve"));
    }
}
