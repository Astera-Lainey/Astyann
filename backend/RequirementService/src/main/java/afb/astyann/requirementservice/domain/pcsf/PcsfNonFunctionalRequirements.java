package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfNonFunctionalRequirements {
    private FieldValue<Integer> concurrentUsers;
    private FieldValue<Integer> targetResponseTimeMs;
    private FieldValue<String>  dataVolumeDescription;
    private FieldValue<String>  availabilityTarget;
    private FieldValue<String>  securityDepth;
    private FieldValue<String>  locale;
}
