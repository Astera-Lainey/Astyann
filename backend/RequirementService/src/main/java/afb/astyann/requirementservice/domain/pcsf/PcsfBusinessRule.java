package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias({"module"})
    private String moduleId;

    @JsonAlias({"useCase", "use_case"})
    private String useCaseId;

    @JsonAlias({"entity", "entityId", "entity_id", "affectedEntity"})
    private String affectedEntityId;

    private FieldValue<String> description;

    @JsonAlias({"hint", "implementation", "technicalHint", "technical_hint", "implementation_hint"})
    private FieldValue<String> implementationHint;
}
