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
        Document doc = toDocument(dto);
        List<Document> chunks = textSplitter.apply(List.of(doc));
        vectorStore.add(chunks);
        log.debug("Indexed document section='{}' for projectId={} ({} chunk(s))",
                  dto.getMetadata() != null ? dto.getMetadata().get("section") : "?",
                  dto.getProjectId(), chunks.size());
    }

    @Override
    public void indexBatch(BatchIndexRequestDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            log.warn("indexBatch called with empty items list for projectId={}", dto.getProjectId());
            return;
        }
        List<Document> docs = dto.getItems().stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
        List<Document> chunks = textSplitter.apply(docs);
        vectorStore.add(chunks);
        log.info("Indexed {} documents ({} chunks) for projectId={}",
                docs.size(), chunks.size(), dto.getProjectId());
    }

    @Override
    public RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK, String sourceTypeFilter) {
        String filterExpr = buildFilter(projectId, sourceTypeFilter);
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
        String context = String.join("\n\n---\n\n", chunks);

        log.debug("Retrieved {} chunks for projectId={} query='{}'", docs.size(), projectId, query);
        return RetrievalResponseDTO.builder()
                .context(context)
                .chunks(chunks)
                .sources(sources)
                .scores(scores)
                .build();
    }

    @Override
    public void deleteIndex(UUID projectId) {
        // Search all chunks for this project, then delete by ID.
        // High topK covers typical PCSF sizes (14 sections × possible sub-chunks).
        SearchRequest request = SearchRequest.builder()
                .query("project requirements entities modules actors screens")
                .topK(500)
                .filterExpression(buildFilter(projectId, null))
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream().map(Document::getId).collect(Collectors.toList());
            vectorStore.delete(ids);
        }
        log.info("Deleted {} chunks for projectId={}", docs.size(), projectId);
    }

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
        return new Document(UUID.randomUUID().toString(), item.getContent(), meta);
    }

    private String buildFilter(UUID projectId, String sourceTypeFilter) {
        String filter = "projectId == '" + projectId + "'";
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            filter += " && sourceType == '" + sourceTypeFilter + "'";
        }
        return filter;
    }
}
