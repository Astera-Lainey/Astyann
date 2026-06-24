package afb.astyann.aiorchestrator.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MergeRequestDTO {
    @NotNull
    private UUID projectId;
    private String documentContext;
    private List<AnswerItemDTO> answers;

    @Data
    public static class AnswerItemDTO {
        private String question;
        private String answer;
    }
}
