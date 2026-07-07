package afb.astyann.requirementservice.repository;

import afb.astyann.requirementservice.domain.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

    Optional<Requirement> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);
}
