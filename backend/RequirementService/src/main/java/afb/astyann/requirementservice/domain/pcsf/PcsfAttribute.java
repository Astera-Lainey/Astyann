package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias({"column", "column_name", "dbColumn"})
    private FieldValue<String>  columnName;

    @JsonAlias({"type", "dataType", "java_type", "fieldType"})
    private FieldValue<String>  javaType;

    @JsonAlias({"dbType", "mysql_type", "sqlType"})
    private FieldValue<String>  mysqlType;

    private PcsfConstraints     constraints;
    private FieldValue<Boolean> showInList;
    private FieldValue<Boolean> showInForm;
}
