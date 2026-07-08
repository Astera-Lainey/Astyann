package afb.astyann.versionservice.repository;

import afb.astyann.versionservice.domain.Timeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TimelineRepository extends JpaRepository<Timeline, UUID> {
    Optional<Timeline> findByProjectId(UUID projectId);
}
