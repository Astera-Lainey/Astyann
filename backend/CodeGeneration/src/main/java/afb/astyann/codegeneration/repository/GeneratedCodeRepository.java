package afb.astyann.codegeneration.repository;

import afb.astyann.codegeneration.domain.CodeLayer;
import afb.astyann.codegeneration.domain.GeneratedCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedCodeRepository extends JpaRepository<GeneratedCode, UUID> {

    List<GeneratedCode> findByProjectId(UUID projectId);

    Optional<GeneratedCode> findByProjectIdAndLayer(UUID projectId, CodeLayer layer);

    void deleteByProjectId(UUID projectId);
}
