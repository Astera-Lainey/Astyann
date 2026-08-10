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
    /**
     * Not a field: whether the endpoint takes a path variable is entirely determined by whether it
     * has any, and holding both separately let a caller set this true while leaving
     * {@code pathVariables} empty — which renders a controller method carrying
     * {@code @GetMapping("/{id}")} and no {@code @PathVariable} parameter at all. Deriving it means
     * the two can never disagree.
     */
    public boolean isHasPathVariable() {
        return pathVariables != null && !pathVariables.isEmpty();
    }
    private String requestBodyType;
    private String responseType;
    /** True for the standard CRUD operations; false for custom use-case actions. */
    @Builder.Default private boolean crud = true;

    /**
     * True for the collection endpoint, which takes a {@code Pageable} and returns a
     * {@code Page<T>}. The generated frontend expects Spring's page envelope
     * ({@code content}, {@code totalPages}, {@code number}, …) — returning a bare list here left
     * the generated list view permanently empty.
     */
    @Builder.Default private boolean paged = false;
    @Builder.Default private List<String> roles = new ArrayList<>();

    /**
     * Path variable names in declaration order, as they appear in the path — {@code productId} for
     * {@code /{productId}/archive}, not a generic {@code id}.
     *
     * <p>The PCSF declares its own variable names, and the controller, the service interface and
     * the service implementation must all agree on them, so the name is carried here rather than
     * hardcoded in three templates. Empty for collection endpoints.
     */
    @Builder.Default private List<String> pathVariables = new ArrayList<>();

    /**
     * The variable a single-row CRUD body operates on ({@code repository.findById(...)}). Defaults
     * to {@code id} so a module generated from CRUD conventions, which declares no names, renders
     * exactly as it always did.
     */
    @Builder.Default private String idVariable = "id";
}
