package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfConstraints {
    private FieldValue<Boolean> required;
    private FieldValue<Boolean> unique;
    private FieldValue<Integer> minLength;
    private FieldValue<Integer> maxLength;
    private FieldValue<String>  pattern;
}
