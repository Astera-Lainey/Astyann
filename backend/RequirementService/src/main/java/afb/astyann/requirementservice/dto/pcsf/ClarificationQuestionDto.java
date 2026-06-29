package afb.astyann.requirementservice.dto.pcsf;

import afb.astyann.requirementservice.domain.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ClarificationQuestionDto {
    private String id;
    private String inventoryRef;
    private String targetPath;
    private Integer priority;
    private String question;
    private QuestionType type;
    private List<String> options;
    private String placeholder;
    private boolean answered;
    private String answer;
}
