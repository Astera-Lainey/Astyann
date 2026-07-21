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
public class BackendEndpoint {
    private String httpMethod;
    private String path;
    private String methodName;
    private String returnType;
    private boolean hasRequestBody;
    private boolean hasPathVariable;
    private String requestBodyType;
    private String responseType;
    /** True for the standard CRUD operations; false for custom use-case actions. */
    @Builder.Default private boolean crud = true;
    @Builder.Default private List<String> roles = new ArrayList<>();
}
