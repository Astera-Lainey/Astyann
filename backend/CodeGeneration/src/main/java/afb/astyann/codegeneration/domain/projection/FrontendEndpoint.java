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

    /**
     * The HTTP verb as an {@code HttpClient} method name — {@code get}, {@code post}, {@code put},
     * {@code patch}, {@code delete}.
     *
     * <p>Custom actions used to be emitted as POST unconditionally, which was safe only while
     * every non-CRUD endpoint was a generated {@code POST /{id}/<action>}. A PCSF that declares its
     * own contract also declares {@code PATCH .../archive} and {@code GET .../export}, and calling
     * either with POST returns 405.
     */
    private String httpMethodLower;

    /** Whether the {@code HttpClient} call takes a body argument at all — POST, PUT and PATCH do. */
    private boolean sendsBody;
}
