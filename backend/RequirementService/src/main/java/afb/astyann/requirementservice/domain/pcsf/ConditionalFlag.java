package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConditionalFlag {
    @Builder.Default
    private boolean triggered = false;
    private FieldValue<Boolean> required;
    private Map<String, Object> details;
}
