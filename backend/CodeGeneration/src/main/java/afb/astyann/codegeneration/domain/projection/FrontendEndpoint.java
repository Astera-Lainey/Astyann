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

    /** False for use-case actions the backend exposes alongside the standard CRUD operations. */
    @Builder.Default private boolean crud = true;

    /**
     * The path segment identifying a custom action — {@code "archive"} for
     * {@code POST /{id}/archive}. Used to build the request URL without re-deriving it from
     * {@link #path} in the template.
     */
    private String actionSegment;

    /** {@code methodName} in PascalCase, for generating a component handler ({@code onArchive}). */
    private String methodNamePascal;

    /** Human-readable button text, e.g. {@code "Record a stock entry"}. */
    private String label;
}
