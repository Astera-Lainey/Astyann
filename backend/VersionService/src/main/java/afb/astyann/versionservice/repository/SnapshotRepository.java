package afb.astyann.versionservice.repository;

import afb.astyann.versionservice.domain.ArtifactType;
import afb.astyann.versionservice.domain.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, UUID> {
    List<Snapshot> findByTimelineIdOrderBySnapDateAsc(UUID timelineId);

    Optional<Snapshot> findTopByTimelineIdAndArtifactTypeAndArtifactIdOrderByVersionNumberDesc(
            UUID timelineId, ArtifactType artifactType, UUID artifactId);

    Optional<Snapshot> findByTimelineIdAndArtifactTypeAndArtifactIdAndActiveTrue(
            UUID timelineId, ArtifactType artifactType, UUID artifactId);

    void deleteByTimelineId(UUID timelineId);
}
