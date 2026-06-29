package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfAttribute {
    private String id;
    private FieldValue<String>  name;
    private FieldValue<String>  columnName;
    private FieldValue<String>  javaType;
    private FieldValue<String>  mysqlType;
    private PcsfConstraints     constraints;
    private FieldValue<Boolean> showInList;
    private FieldValue<Boolean> showInForm;
}
