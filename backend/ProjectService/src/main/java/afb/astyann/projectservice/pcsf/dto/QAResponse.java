package afb.astyann.projectservice.pcsf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QAResponse {
    private String pcsfStatus;
    private int pendingQuestionsCount;
    private List<String> missingItems;
}
