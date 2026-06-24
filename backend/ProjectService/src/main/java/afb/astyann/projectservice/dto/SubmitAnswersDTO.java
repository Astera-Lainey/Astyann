package afb.astyann.projectservice.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SubmitAnswersDTO {
    private List<AnswerItemDTO> answers;

    @Data
    public static class AnswerItemDTO {
        private UUID gqId;
        private String answer;
    }
}
