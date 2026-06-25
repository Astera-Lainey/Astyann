package afb.astyann.projectservice.repository;

import afb.astyann.projectservice.domain.ClarificationQuestion;
import afb.astyann.projectservice.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClarificationQuestionRepository extends JpaRepository<ClarificationQuestion, String> {

    List<ClarificationQuestion> findByProjectOrderByPriorityAsc(Project project);

    List<ClarificationQuestion> findByProject_ProjectIdOrderByPriorityAsc(UUID projectId);

    List<ClarificationQuestion> findByProject_ProjectIdAndAnsweredFalseOrderByPriorityAsc(UUID projectId);

    int countByProject_ProjectIdAndAnsweredFalse(UUID projectId);

    void deleteByProject(Project project);
}
