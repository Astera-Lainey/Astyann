package afb.astyann.projectservice.pcsf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfConditionalFeatures {
    @Builder.Default private ConditionalFlag fileUpload   = new ConditionalFlag();
    @Builder.Default private ConditionalFlag dataExport   = new ConditionalFlag();
    @Builder.Default private ConditionalFlag searchFilter = new ConditionalFlag();
    @Builder.Default private ConditionalFlag multiTenancy = new ConditionalFlag();
}
