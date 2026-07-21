package afb.astyann.documentservice.repository;

import afb.astyann.documentservice.domain.DocumentVersionArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionArchiveRepository extends JpaRepository<DocumentVersionArchive, UUID> {
    Optional<DocumentVersionArchive> findBySnapshotIdAndDocumentId(UUID snapshotId, UUID documentId);
    void deleteByProjectId(UUID projectId);
}
