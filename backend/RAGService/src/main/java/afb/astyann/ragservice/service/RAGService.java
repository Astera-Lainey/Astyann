package afb.astyann.ragservice.service;

import afb.astyann.ragservice.dto.BatchIndexRequestDTO;
import afb.astyann.ragservice.dto.IndexRequestDTO;
import afb.astyann.ragservice.dto.RetrievalResponseDTO;

import java.util.UUID;

public interface RAGService {
    void indexDocument(IndexRequestDTO dto);
    void indexBatch(BatchIndexRequestDTO dto);
    RetrievalResponseDTO retrieveContext(UUID projectId, String query, int topK, String sourceTypeFilter);
    void deleteIndex(UUID projectId);
    void rebuildIndex(UUID projectId);
}
