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
public class PcsfModule {
    private String id;
    private FieldValue<String> name;
    private FieldValue<String> description;
    private FieldValue<List<String>> crudOperations;
    @Builder.Default
    private List<PcsfUseCase> useCases = new ArrayList<>();
}
