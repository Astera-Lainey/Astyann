package afb.astyann.diagramgeneratorservice.repository;

import afb.astyann.diagramgeneratorservice.domain.DiagramVersionArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiagramVersionArchiveRepository extends JpaRepository<DiagramVersionArchive, UUID> {
    Optional<DiagramVersionArchive> findBySnapshotIdAndDiagramId(UUID snapshotId, UUID diagramId);
    void deleteByProjectId(UUID projectId);
}
