package afb.astyann.documentservice.service;

import afb.astyann.documentservice.client.RAGServiceClient;
import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.dto.RagIndexItemDTO;
import afb.astyann.documentservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentRagIndexingService {

    private final DocumentRepository repository;
    private final RAGServiceClient ragServiceClient;

    /**
     * Indexes a document's text for retrieval.
     *
     * <p>Called only once a document is APPROVED — and again whenever the approved content changes
     * (a restore to an earlier snapshot, or a late snapshot stamp). Indexing at generation time
     * instead would put text into the index that a reviewer had not accepted, and might never
     * accept, with nothing downstream able to tell the difference.
     *
     * <p>RAGService replaces by {@code sourceId}, so calling this repeatedly for one document
     * leaves exactly one version stored: the current one.
     *
     * <p>Reads the document's current {@code snapshotId} from the database rather than taking it
     * as an argument, so a caller inside a transaction cannot stamp a snapshot that later rolls
     * back. A null snapshotId is indexed as-is — the content is still correct, and
     * {@code retryPendingSnapshots} re-indexes once the snapshot lands.
     */
    @Async("documentExecutor")
    public void indexApprovedDocumentAsync(UUID documentId) {
        try {
            Document doc = repository.findById(documentId).orElse(null);
            if (doc == null || doc.getPath() == null) {
                log.warn("Skipping RAG indexing for documentId={} — document or path missing", documentId);
                return;
            }
            String text;
            try (FileInputStream fis = new FileInputStream(doc.getPath());
                 XWPFDocument xwpf = new XWPFDocument(fis);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(xwpf)) {
                text = extractor.getText();
            }
            ragServiceClient.indexDocument(RagIndexItemDTO.builder()
                    .projectId(doc.getProjectId())
                    .sourceType("DOCUMENT")
                    .sourceId(doc.getDocumentId())
                    .snapshotId(doc.getSnapshotId())
                    .content(text)
                    .metadata(Map.of("documentType", doc.getType().name()))
                    .build());
            log.info("RAG indexing completed for documentId={} (snapshotId={})",
                    documentId, doc.getSnapshotId());
        } catch (Exception ex) {
            // Indexing failure must never affect the document's approved status.
            log.warn("RAG indexing failed for documentId={}: {}", documentId, ex.getMessage());
        }
    }

}
