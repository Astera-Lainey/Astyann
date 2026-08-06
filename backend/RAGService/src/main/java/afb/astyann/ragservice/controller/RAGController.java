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
            @RequestParam String projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        RetrievalResponseDTO response = ragService.retrieveContext(parseId(projectId), query, topK, null);
        return ResponseEntity.ok(response.getContext());
    }

    /** Returns list of source section names — matches AIOrchestrator RAGServiceClient */
    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources(
            @RequestParam String projectId,
            @RequestParam String query) {
        RetrievalResponseDTO response = ragService.retrieveContext(parseId(projectId), query, 10, null);
        return ResponseEntity.ok(response.getSources());
    }

    /** Full retrieval response with chunks, sources, and scores */
    @GetMapping("/retrieve")
    public ResponseEntity<RetrievalResponseDTO> retrieve(
            @RequestParam String projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String sourceTypeFilter) {
        return ResponseEntity.ok(ragService.retrieveContext(parseId(projectId), query, topK, sourceTypeFilter));
    }

    /**
     * Removes one source's chunks — for a deleted document, or to drop stale text without
     * clearing the whole project index.
     */
    @DeleteMapping("/{projectId}/source/{sourceId}")
    public ResponseEntity<Map<String, Object>> deleteBySource(
            @PathVariable String projectId,
            @PathVariable String sourceId,
            @RequestParam(required = false) String sourceType) {
        int removed = ragService.deleteBySource(parseId(projectId), sourceType, parseId(sourceId));
        return ResponseEntity.ok(Map.of("sourceId", sourceId, "chunksRemoved", removed));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteIndex(@PathVariable String projectId) {
        ragService.deleteIndex(parseId(projectId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/rebuild")
    public ResponseEntity<Map<String, String>> rebuildIndex(@PathVariable String projectId) {
        ragService.rebuildIndex(parseId(projectId));
        return ResponseEntity.ok(Map.of(
                "status",  "INDEX_CLEARED",
                "message", "Index cleared for project " + projectId +
                           ". Re-trigger via POST /api/v1/requirements/" + projectId + "/approve"));
    }
    private UUID parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header is missing");
        }
        String s = rawId.trim();
        // Strip 0x prefix if present
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        // Insert dashes if raw 32-char hex (no dashes)
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
