package afb.astyann.ragservice.service;

import afb.astyann.ragservice.dto.BatchIndexRequestDTO;
import afb.astyann.ragservice.dto.IndexRequestDTO;
import afb.astyann.ragservice.dto.RetrievalResponseDTO;

import java.util.UUID;

public interface RAGService {

    /** Indexes one source, replacing anything already stored for the same {@code sourceId}. */
    void indexDocument(IndexRequestDTO dto);

    /** Indexes many sources, replacing anything already stored for each {@code sourceId}. */
    void indexBatch(BatchIndexRequestDTO dto);

    RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK, String sourceTypeFilter);

    /**
     * Removes every chunk belonging to one source — used when a document is deleted, or before
     * re-indexing it.
     *
     * @return how many chunks were removed
     */
    int deleteBySource(UUID projectId, String sourceType, UUID sourceId);

    void deleteIndex(UUID projectId);
    void rebuildIndex(UUID projectId);
}
