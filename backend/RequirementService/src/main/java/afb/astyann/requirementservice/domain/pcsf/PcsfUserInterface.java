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
public class PcsfUserInterface {
    @Builder.Default private PcsfColours     colours    = new PcsfColours();
    @Builder.Default private List<PcsfScreen>  screens    = new ArrayList<>();
    @Builder.Default private List<PcsfNavItem> navigation = new ArrayList<>();
}
