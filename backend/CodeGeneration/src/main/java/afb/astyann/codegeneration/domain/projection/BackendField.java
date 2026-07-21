package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendField {
    private String name;
    private String columnName;
    private String javaType;
    private boolean required;
    private boolean unique;
    private Integer minLength;
    private Integer maxLength;
    private boolean id;
}
