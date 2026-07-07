package afb.astyann.requirementservice.domain.pcsf;

import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
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
public class PcsfPublicAccess {
    @Builder.Default
    private FieldValue<Boolean> hasPublicActor = FieldValue.<Boolean>builder()
            .value(false)
            .source(FieldSource.DEFAULT)
            .status(FieldStatus.CONFIRMED)
            .build();
    @Builder.Default
    private List<String> publicPaths = new ArrayList<>(List.of("/api/v1/auth/**"));
}
