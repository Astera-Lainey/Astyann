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

    @Async("documentExecutor")
    public void indexDocumentAsync(UUID documentId) {
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
                    .content(text)
                    .metadata(Map.of("documentType", doc.getType().name()))
                    .build());
            log.info("RAG indexing completed for documentId={}", documentId);
        } catch (Exception ex) {
            // Indexing failure must never affect the document's generated status.
            log.warn("RAG indexing failed for documentId={}: {}", documentId, ex.getMessage());
        }
    }
}
