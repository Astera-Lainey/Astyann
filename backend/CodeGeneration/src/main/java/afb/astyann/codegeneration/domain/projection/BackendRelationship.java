package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendRelationship {
    private String fieldName;
    private String targetEntity;
    /** ONE_TO_MANY | MANY_TO_ONE | ONE_TO_ONE | MANY_TO_MANY. */
    private String relationType;
    private String joinColumn;
    private boolean owning;
}
