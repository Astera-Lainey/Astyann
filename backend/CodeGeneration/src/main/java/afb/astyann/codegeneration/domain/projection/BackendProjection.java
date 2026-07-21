package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat, envelope-free view of the PCSF consumed by the backend FreeMarker templates.
 * All values are plain (no {@code FieldValue} wrappers) so templates can reference them directly.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendProjection {
    private BackendProjectInfo projectInfo;
    @Builder.Default private List<BackendEntity> entities = new ArrayList<>();
    @Builder.Default private List<BackendModule> modules = new ArrayList<>();
    @Builder.Default private List<BackendRole> roles = new ArrayList<>();
}
