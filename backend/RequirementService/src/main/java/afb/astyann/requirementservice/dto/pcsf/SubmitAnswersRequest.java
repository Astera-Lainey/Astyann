package afb.astyann.requirementservice.dto.pcsf;

import lombok.Data;

import java.util.List;

@Data
public class SubmitAnswersRequest {
    private List<AnswerSubmission> answers;

    @Data
    public static class AnswerSubmission {
        private String questionId;
        private String answer;
    }
}
