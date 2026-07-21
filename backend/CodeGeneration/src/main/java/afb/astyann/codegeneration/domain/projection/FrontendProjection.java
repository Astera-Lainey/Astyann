package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendProjection {
    private FrontendProjectInfo projectInfo;
    @Builder.Default private List<FrontendEntity> entities = new ArrayList<>();
    @Builder.Default private List<FrontendModule> modules = new ArrayList<>();
    @Builder.Default private List<FrontendNavItem> navigation = new ArrayList<>();
}
