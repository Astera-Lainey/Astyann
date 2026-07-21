package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendEntity {
    private String className;
    private String fileName;
    private String instanceName;
    @Builder.Default private List<FrontendField> fields = new ArrayList<>();
}
