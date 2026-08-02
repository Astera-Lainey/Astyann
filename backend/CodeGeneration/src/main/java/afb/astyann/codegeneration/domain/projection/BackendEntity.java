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
public class BackendEntity {
    private String className;
    private String tableName;
    private String instanceName;
    private boolean audited;
    /** UUID or IDENTITY. */
    private String idStrategy;

    /**
     * The Java type of the primary key — {@code Long} for {@code IDENTITY}, otherwise {@code UUID}.
     *
     * <p>Every template that mentions an id must use this. Entity/Repository/ResponseDto once
     * honoured {@code idStrategy} while Controller/Service hardcoded {@code UUID}, so an
     * {@code IDENTITY} entity produced a project that could not compile — and one the AI fix loop
     * could never repair, because satisfying the interface broke the impl and vice versa.
     */
    @Builder.Default private String idType = "UUID";
    @Builder.Default private List<BackendField> fields = new ArrayList<>();
    @Builder.Default private List<BackendRelationship> relationships = new ArrayList<>();

    /**
     * Whether a persist round-trip test can be generated for this entity — true when every
     * {@code required} field has a {@link BackendField#getSampleValue() sample value}. When false
     * the generated repository test falls back to a read-only assertion, because we cannot build
     * an instance that satisfies the NOT NULL constraints.
     */
    @Builder.Default private boolean testable = true;
}
