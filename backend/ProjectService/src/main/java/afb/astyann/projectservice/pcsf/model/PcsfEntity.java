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
public class PcsfEntity {
    private String id;
    private FieldValue<String>  name;
    private FieldValue<String>  tableName;
    private String primaryModuleId;
    @Builder.Default private boolean auditFields       = true;
    @Builder.Default private String  primaryKeyStrategy = "UUID";
    private FieldValue<Boolean> softDelete;
    @Builder.Default private List<PcsfAttribute> attributes = new ArrayList<>();
}
