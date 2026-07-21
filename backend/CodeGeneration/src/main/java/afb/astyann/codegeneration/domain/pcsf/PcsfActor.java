package afb.astyann.codegeneration.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfActor {
    private String id;
    private FieldValue<String> name;
    private FieldValue<String> type;
    private FieldValue<String> description;
    private FieldValue<String> springSecurityRole;
}
