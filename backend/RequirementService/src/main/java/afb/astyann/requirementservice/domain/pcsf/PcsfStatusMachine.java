package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"entity", "entity_id"})
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

        /** The event/method that fires this transition, e.g. "approve", "submit" */
        @JsonAlias({"event", "triggerEvent", "on"})
        private String trigger;

        /** Optional guard condition, e.g. "balance > 0" */
        @JsonAlias({"condition", "when", "if"})
        private String guard;

        /** Optional side-effect description, e.g. "sendNotification()" */
        @JsonAlias({"effect", "sideEffect", "onTransition", "callback"})
        private String action;
    }
}
