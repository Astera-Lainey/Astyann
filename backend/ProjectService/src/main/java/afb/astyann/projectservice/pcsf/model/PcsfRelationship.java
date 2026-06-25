package afb.astyann.projectservice.pcsf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfRelationship {
    private String id;
    private String fromEntityId;
    private String toEntityId;
    private FieldValue<String> cardinality;
    private FieldValue<String> optionality;
    private String owningEntityId;
    private String joinColumnName;
    private String joinTableName;
    private FieldValue<String> label;
}
