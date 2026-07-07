package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.ClarificationQuestion;
import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.dto.pcsf.QAResponse;
import afb.astyann.requirementservice.dto.pcsf.SubmitAnswersRequest;
import afb.astyann.requirementservice.exception.RequirementNotFoundException;
import afb.astyann.requirementservice.repository.ClarificationQuestionRepository;
import afb.astyann.requirementservice.repository.RequirementRepository;
import afb.astyann.requirementservice.util.PcsfFieldWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QAService {

    private final RequirementRepository           requirementRepository;
    private final ClarificationQuestionRepository questionRepository;
    private final PcsfFieldWriter                 fieldWriter;
    private final ObjectMapper                    objectMapper;
    private final PcsfCompletenessAnalyser        completenessAnalyser;

    @Transactional
    public QAResponse submitAnswers(UUID projectId, SubmitAnswersRequest request) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));

        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            int pending = questionRepository.countByRequirement_RequirementIdAndAnsweredFalse(
                    requirement.getRequirementId());
            return QAResponse.builder()
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .pendingQuestionsCount(pending)
                    .build();
        }

        Pcsf pcsf;
        try {
            pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize PCSF for requirement={}", requirement.getRequirementId(), ex);
            return QAResponse.builder()
                    .pcsfStatus(requirement.getPcsfStatus().name())
                    .build();
        }

        for (SubmitAnswersRequest.AnswerSubmission submission : request.getAnswers()) {
            if (submission.getQuestionId() == null || submission.getAnswer() == null) continue;

            questionRepository.findById(submission.getQuestionId()).ifPresent(q -> {
                q.setAnswer(submission.getAnswer());
                q.setAnswered(true);
                questionRepository.save(q);
                fieldWriter.write(pcsf, q.getTargetPath(), submission.getAnswer());
            });
        }

        try {
            requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        } catch (Exception ex) {
            log.error("Failed to serialize updated PCSF for requirement={}", requirement.getRequirementId(), ex);
        }

        int remaining = questionRepository.countByRequirement_RequirementIdAndAnsweredFalse(
                requirement.getRequirementId());
        requirement.setPendingQuestionsCount(remaining);

        if (remaining == 0) {
            requirement.setPcsfStatus(PcsfStatus.INFERRING);
            requirementRepository.save(requirement);
            completenessAnalyser.analyse(requirement);
        } else {
            requirementRepository.save(requirement);
        }

        return QAResponse.builder()
                .pcsfStatus(requirement.getPcsfStatus().name())
                .pendingQuestionsCount(remaining)
                .build();
    }

    public List<ClarificationQuestion> getPendingQuestions(UUID projectId) {
        Requirement requirement = requirementRepository.findByProjectId(projectId)
                .orElseThrow(() -> new RequirementNotFoundException(projectId));
        return questionRepository.findByRequirement_RequirementIdAndAnsweredFalseOrderByPriorityAsc(
                requirement.getRequirementId());
    }
}
