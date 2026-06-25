package afb.astyann.projectservice.repository;

import afb.astyann.projectservice.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByProjectId(UUID projectId);

    List<Project> findByUserId(UUID userId);

    List<Project> findByUserIdAndTitleContaining(UUID userId, String query);

    void deleteByProjectId(UUID projectId);
}
