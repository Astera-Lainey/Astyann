package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfProject {
    private FieldValue<String> name;
    private FieldValue<String> description;
    private String organisationName = "Afriland First Bank";
    private FieldValue<String> displayName;
    private PcsfDerivedNames derived;
}
