package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfBusinessRule {
    private String id;
    private String moduleId;
    private String useCaseId;
    private String affectedEntityId;
    private FieldValue<String> description;
    private FieldValue<String> implementationHint;
}
