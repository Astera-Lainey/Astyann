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

    /**
     * True for the collection endpoint, which takes a {@code Pageable} and returns a
     * {@code Page<T>}. The generated frontend expects Spring's page envelope
     * ({@code content}, {@code totalPages}, {@code number}, …) — returning a bare list here left
     * the generated list view permanently empty.
     */
    @Builder.Default private boolean paged = false;
    @Builder.Default private List<String> roles = new ArrayList<>();
}
