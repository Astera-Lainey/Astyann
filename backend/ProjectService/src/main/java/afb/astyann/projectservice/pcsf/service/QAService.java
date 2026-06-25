package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.domain.ClarificationQuestion;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.exception.ProjectNotFoundException;
import afb.astyann.projectservice.pcsf.dto.QAResponse;
import afb.astyann.projectservice.pcsf.dto.SubmitAnswersRequest;
import afb.astyann.projectservice.pcsf.model.Pcsf;
import afb.astyann.projectservice.pcsf.util.PcsfFieldWriter;
import afb.astyann.projectservice.repository.ClarificationQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QAService {

    private final ProjectRepository               projectRepository;
    private final ClarificationQuestionRepository questionRepository;
    private final ObjectMapper                    objectMapper;
    private final PcsfFieldWriter                 fieldWriter;
    private final PcsfCompletenessAnalyser        analyser;

    @Transactional
    public QAResponse submitAnswers(UUID projectId, SubmitAnswersRequest request) {
        log.info("Submitting {} answers for project={}", request.getAnswers().size(), projectId);

        Project project = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));

        if (project.getPcsfJson() == null) {
            throw new IllegalStateException("PCSF not initialised for project " + projectId);
        }

        Pcsf pcsf;
        try {
            pcsf = objectMapper.readValue(project.getPcsfJson(), Pcsf.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse PCSF for project " + projectId, ex);
        }

        for (SubmitAnswersRequest.AnswerSubmission submission : request.getAnswers()) {
            ClarificationQuestion question = questionRepository.findById(submission.getQuestionId())
                    .orElse(null);
            if (question == null) {
                log.warn("Question {} not found, skipping", submission.getQuestionId());
                continue;
            }
            fieldWriter.write(pcsf, question.getTargetPath(), submission.getAnswer());
            question.setAnswer(submission.getAnswer());
            question.setAnswered(true);
            questionRepository.save(question);
        }

        try {
            project.setPcsfJson(objectMapper.writeValueAsString(pcsf));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise PCSF", ex);
        }

        int remaining = questionRepository.countByProject_ProjectIdAndAnsweredFalse(projectId);
        project.setPendingQuestionsCount(remaining);
        projectRepository.save(project);

        PcsfCompletenessAnalyser.ValidationResult result = analyser.analyse(project);

        String status = result.gate1Passed() ? "GATE_1_PASSED" : "DRAFT";
        if (result.gate1Passed()) status = "INFERRING";

        return QAResponse.builder()
                .pcsfStatus(status)
                .pendingQuestionsCount(project.getPendingQuestionsCount())
                .missingItems(result.missing())
                .build();
    }
}
