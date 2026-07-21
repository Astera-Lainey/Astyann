package afb.astyann.codegeneration.repository;

import afb.astyann.codegeneration.domain.CodeVersionArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CodeVersionArchiveRepository extends JpaRepository<CodeVersionArchive, UUID> {

    Optional<CodeVersionArchive> findBySnapshotIdAndCodeId(UUID snapshotId, UUID codeId);

    void deleteByProjectId(UUID projectId);
}
