package afb.astyann.ragservice.service.impl;

import afb.astyann.ragservice.dto.BatchIndexRequestDTO;
import afb.astyann.ragservice.dto.IndexRequestDTO;
import afb.astyann.ragservice.dto.RetrievalResponseDTO;
import afb.astyann.ragservice.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGServiceImpl implements RAGService {

    private final VectorStore vectorStore;

    // Callers (RequirementService, etc.) send whole PCSF sections as single
    // documents with no size limit — split here so no single chunk can exceed
    // the embedding model's max input token count.
    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    @Override
    public void indexDocument(IndexRequestDTO dto) {
        int removed = replaceExisting(List.of(dto));
        Document doc = toDocument(dto);
        List<Document> chunks = textSplitter.apply(List.of(doc));
        vectorStore.add(chunks);
        log.debug("Indexed document section='{}' for projectId={} ({} chunk(s) added, {} superseded chunk(s) removed)",
                  dto.getMetadata() != null ? dto.getMetadata().get("section") : "?",
                  dto.getProjectId(), chunks.size(), removed);
    }

    @Override
    public void indexBatch(BatchIndexRequestDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            log.warn("indexBatch called with empty items list for projectId={}", dto.getProjectId());
            return;
        }
        int removed = replaceExisting(dto.getItems());
        List<Document> docs = dto.getItems().stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
        List<Document> chunks = textSplitter.apply(docs);
        vectorStore.add(chunks);
        log.info("Indexed {} documents ({} chunks added, {} superseded chunks removed) for projectId={}",
                docs.size(), chunks.size(), removed, dto.getProjectId());
    }

    /**
     * Drops whatever is already stored for each distinct source about to be written.
     *
     * <p>{@code vectorStore.add} only ever appends, so without this a regenerated document or a
     * re-approved requirement leaves its previous text in the store alongside the new text. Both
     * versions then compete in similarity search, and a caller can be handed prose that was
     * superseded — with nothing in the result to say so. Indexing the same source twice must
     * leave the store holding one version: the latest.
     *
     * @return how many superseded chunks were removed, for logging
     */
    private int replaceExisting(List<IndexRequestDTO> items) {
        Set<String> seen = new HashSet<>();
        int removed = 0;
        for (IndexRequestDTO item : items) {
            if (item == null || item.getProjectId() == null || item.getSourceId() == null) continue;
            String key = item.getProjectId() + "|" + item.getSourceType() + "|" + item.getSourceId();
            // A batch usually carries many chunks of one source; delete once per source, not per chunk,
            // or the second chunk's delete would wipe the first chunk we just wrote.
            if (!seen.add(key)) continue;
            removed += deleteBySource(item.getProjectId(), item.getSourceType(), item.getSourceId());
        }
        return removed;
    }

    @Override
    public int deleteBySource(UUID projectId, String sourceType, UUID sourceId) {
        if (projectId == null || sourceId == null) return 0;
        List<Document> existing = findAll(buildSourceFilter(projectId, sourceType, sourceId));
        if (existing.isEmpty()) return 0;
        vectorStore.delete(existing.stream().map(Document::getId).collect(Collectors.toList()));
        log.debug("Removed {} chunk(s) for sourceId={} (projectId={})", existing.size(), sourceId, projectId);
        return existing.size();
    }

    @Override
    public RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK, String sourceTypeFilter) {
        return retrieveContext(projectId, query, topK, sourceTypeFilter, null, null);
    }

    @Override
    public RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK,
                                                String sourceTypeFilter, List<String> documentTypes,
                                                List<String> snapshotIds) {
        String filterExpr = buildScopedFilter(projectId, sourceTypeFilter, documentTypes, snapshotIds);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpr)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);

        List<String> chunks  = docs.stream().map(Document::getText).collect(Collectors.toList());
        List<String> sources = docs.stream()
                .map(d -> Objects.toString(d.getMetadata().get("section"), "unknown"))
                .collect(Collectors.toList());
        List<Double> scores  = docs.stream()
                .map(d -> {
                    Object dist = d.getMetadata().get("distance");
                    return dist instanceof Number ? ((Number) dist).doubleValue() : 0.0;
                })
                .collect(Collectors.toList());
        // Positionally aligned with chunks, so a caller can see which source and which approved
        // version each passage came from.
        List<Map<String, Object>> metadata = docs.stream()
                .map(d -> Map.<String, Object>of(
                        "sourceType", Objects.toString(d.getMetadata().get("sourceType"), ""),
                        "sourceId",   Objects.toString(d.getMetadata().get("sourceId"), ""),
                        "snapshotId", Objects.toString(d.getMetadata().get("snapshotId"), "")))
                .collect(Collectors.toList());
        String context = String.join("\n\n---\n\n", chunks);

        log.debug("Retrieved {} chunks for projectId={} query='{}'", docs.size(), projectId, query);
        return RetrievalResponseDTO.builder()
                .context(context)
                .chunks(chunks)
                .sources(sources)
                .scores(scores)
                .metadata(metadata)
                .build();
    }

    @Override
    public void deleteIndex(UUID projectId) {
        List<Document> docs = findAll(buildFilter(projectId, null));
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream().map(Document::getId).collect(Collectors.toList());
            vectorStore.delete(ids);
        }
        log.info("Deleted {} chunks for projectId={}", docs.size(), projectId);
    }

    /**
     * Enumerates every chunk matching {@code filterExpression}.
     *
     * <p>The store exposes no "list by metadata" operation, so this is a similarity search wearing
     * a disguise: a deliberately broad query, a similarity threshold of zero so nothing is filtered
     * on relevance, and a topK high enough to cover a whole project. The metadata filter is what
     * actually selects the rows.
     */
    private List<Document> findAll(String filterExpression) {
        SearchRequest request = SearchRequest.builder()
                .query(ENUMERATION_QUERY)
                .topK(MAX_CHUNKS_PER_PROJECT)
                .similarityThreshold(0.0)
                .filterExpression(filterExpression)
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        return docs == null ? List.of() : docs;
    }

    /** Broad enough that no stored chunk is excluded on relevance when enumerating. */
    private static final String ENUMERATION_QUERY = "project requirements entities modules actors screens";

    /** Upper bound when enumerating one project's chunks; a PCSF plus documents stays well under this. */
    private static final int MAX_CHUNKS_PER_PROJECT = 500;

    @Override
    public void rebuildIndex(UUID projectId) {
        deleteIndex(projectId);
        log.info("Index cleared for projectId={}. Re-trigger indexing via POST /api/v1/requirements/{{projectId}}/approve",
                 projectId);
    }

    private Document toDocument(IndexRequestDTO item) {
        Map<String, Object> meta = new HashMap<>();
        if (item.getMetadata() != null) {
            meta.putAll(item.getMetadata());
        }
        meta.put("projectId",  item.getProjectId()  != null ? item.getProjectId().toString()  : "");
        meta.put("sourceType", item.getSourceType()  != null ? item.getSourceType()            : "");
        meta.put("sourceId",   item.getSourceId()    != null ? item.getSourceId().toString()   : "");
        // Which approved version this text came from. Empty when the caller has no versioning, or
        // when an approval's snapshot has not been confirmed yet and is stamped on retry.
        meta.put("snapshotId", item.getSnapshotId()  != null ? item.getSnapshotId().toString() : "");
        return new Document(UUID.randomUUID().toString(), item.getContent(), meta);
    }

    private String buildFilter(UUID projectId, String sourceTypeFilter) {
        String filter = "projectId == '" + projectId + "'";
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            filter += " && sourceType == '" + sourceTypeFilter + "'";
        }
        return filter;
    }

    /**
     * Narrows retrieval to particular document types and approved versions.
     *
     * <p>Without this a caller asking for context about one module competes against every document,
     * diagram and requirement in the project, including the text of versions that were superseded
     * but whose chunks are still stored under a different snapshot. Both lists are optional and an
     * empty one simply widens the filter back out.
     */
    private String buildScopedFilter(UUID projectId, String sourceTypeFilter,
                                     List<String> documentTypes, List<String> snapshotIds) {
        StringBuilder filter = new StringBuilder(buildFilter(projectId, sourceTypeFilter));
        if (documentTypes != null && !documentTypes.isEmpty()) {
            filter.append(" && documentType in ").append(quotedList(documentTypes));
        }
        if (snapshotIds != null && !snapshotIds.isEmpty()) {
            filter.append(" && snapshotId in ").append(quotedList(snapshotIds));
        }
        return filter.toString();
    }

    private String quotedList(List<String> values) {
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> "'" + v.trim() + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String buildSourceFilter(UUID projectId, String sourceType, UUID sourceId) {
        return buildFilter(projectId, sourceType) + " && sourceId == '" + sourceId + "'";
    }
}
