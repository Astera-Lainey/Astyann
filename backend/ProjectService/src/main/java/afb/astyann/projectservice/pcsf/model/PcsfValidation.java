package afb.astyann.projectservice.pcsf.model;

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
public class PcsfValidation {
    @Builder.Default private double completenessScore      = 0.0;
    @Builder.Default private boolean generationReady       = false;
    @Builder.Default private List<String> missingMandatoryItems  = new ArrayList<>();
    @Builder.Default private List<String> pendingConditionalItems = new ArrayList<>();
    @Builder.Default private List<String> pendingInferredItems   = new ArrayList<>();
    @Builder.Default private List<String> warnings               = new ArrayList<>();
    @Builder.Default private List<String> errors                 = new ArrayList<>();
}
