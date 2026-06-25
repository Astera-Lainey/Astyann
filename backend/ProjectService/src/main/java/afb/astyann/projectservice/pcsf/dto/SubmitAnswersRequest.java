package afb.astyann.projectservice.pcsf.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SubmitAnswersRequest {
    @NotEmpty
    private List<AnswerSubmission> answers;

    @Data
    public static class AnswerSubmission {
        private String questionId;
        private String answer;
    }
}
