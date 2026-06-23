package afb.astyann.projectservice.repository;

import afb.astyann.projectservice.domain.GuidedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GuidedQuestionRepository extends JpaRepository<GuidedQuestion, UUID> {

    List<GuidedQuestion> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
