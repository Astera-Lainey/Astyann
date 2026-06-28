package afb.astyann.requirementservice.repository;

import afb.astyann.requirementservice.domain.ClarificationQuestion;
import afb.astyann.requirementservice.domain.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClarificationQuestionRepository extends JpaRepository<ClarificationQuestion, String> {

    List<ClarificationQuestion> findByRequirementOrderByPriorityAsc(Requirement requirement);

    List<ClarificationQuestion> findByRequirement_RequirementIdOrderByPriorityAsc(UUID requirementId);

    List<ClarificationQuestion> findByRequirement_RequirementIdAndAnsweredFalseOrderByPriorityAsc(UUID requirementId);

    int countByRequirement_RequirementIdAndAnsweredFalse(UUID requirementId);

    void deleteByRequirement(Requirement requirement);
}
