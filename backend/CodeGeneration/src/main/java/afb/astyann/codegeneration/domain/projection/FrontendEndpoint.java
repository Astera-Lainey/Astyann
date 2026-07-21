package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendEndpoint {
    private String methodName;
    private String httpMethod;
    private String path;
    private boolean hasPathId;
    private boolean hasBody;
    private String returnType;
}
