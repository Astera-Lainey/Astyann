package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfUseCase {
    private String id;
    private FieldValue<String> name;
    private String actorId;
    private FieldValue<String> preconditions;
    private FieldValue<String> postconditions;
    private FieldValue<List<String>> mainScenario;
    private FieldValue<String> alternativeScenario;
}
