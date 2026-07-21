package afb.astyann.codegeneration.domain.projection;

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
public class BackendEntity {
    private String className;
    private String tableName;
    private String instanceName;
    private boolean audited;
    /** UUID or IDENTITY. */
    private String idStrategy;
    @Builder.Default private List<BackendField> fields = new ArrayList<>();
    @Builder.Default private List<BackendRelationship> relationships = new ArrayList<>();
}
