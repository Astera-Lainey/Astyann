package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfStatusMachine {
    private String entityId;
    @Builder.Default private List<String>           states      = new ArrayList<>();
    @Builder.Default private List<StatusTransition> transitions = new ArrayList<>();
    private String initialState;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatusTransition {
        private String from;
        private String to;
    }
}
