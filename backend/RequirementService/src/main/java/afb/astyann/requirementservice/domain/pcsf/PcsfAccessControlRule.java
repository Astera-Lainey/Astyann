package afb.astyann.requirementservice.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfAccessControlRule {
    private String id;
    private String moduleId;
    private String entityId;
    private String operation;
    private FieldValue<List<String>> allowedRoles;
}
