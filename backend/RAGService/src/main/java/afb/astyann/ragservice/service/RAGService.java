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

    /**
     * Retrieval narrowed to particular document types and approved snapshots.
     *
     * <p>The unscoped overload searches every chunk in the project — every document type, and every
     * version ever indexed. A caller that knows which documents it cares about, and which approved
     * versions of them, gets far less competition for its top-k slots.
     */
    RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK,
                                         String sourceTypeFilter, java.util.List<String> documentTypes,
                                         java.util.List<String> snapshotIds);

    void deleteIndex(UUID projectId);
    void rebuildIndex(UUID projectId);
}
