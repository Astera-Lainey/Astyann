package afb.astyann.diagramgeneratorservice.repository;

import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UMLDiagramRepository extends JpaRepository<UMLDiagram, UUID> {
    List<UMLDiagram> findByProjectId(UUID projectId);
    Optional<UMLDiagram> findByProjectIdAndType(UUID projectId, DiagramType type);
    void deleteByProjectId(UUID projectId);
}
