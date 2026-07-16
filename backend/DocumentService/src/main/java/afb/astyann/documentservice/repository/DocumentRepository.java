package afb.astyann.documentservice.repository;

import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByProjectId(UUID projectId);
    Optional<Document> findByProjectIdAndType(UUID projectId, DocumentType type);
    List<Document> findByStatusAndSnapshotIdIsNull(DocumentStatus status);
}
