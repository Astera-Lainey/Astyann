package afb.astyann.projectservice.pcsf.model;

import afb.astyann.projectservice.pcsf.model.enums.FieldSource;
import afb.astyann.projectservice.pcsf.model.enums.FieldStatus;
import afb.astyann.projectservice.pcsf.model.enums.RiskLevel;
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
