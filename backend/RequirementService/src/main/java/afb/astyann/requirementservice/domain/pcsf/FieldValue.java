package afb.astyann.requirementservice.domain.pcsf;

import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import afb.astyann.requirementservice.domain.pcsf.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FieldValue<T> {
    private T value;
    private FieldSource source;
    private FieldStatus status;
    private Double confidence;
    private RiskLevel riskLevel;
}
