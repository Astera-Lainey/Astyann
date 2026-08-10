package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendField {
    private String name;
    private String columnName;
    private String javaType;
    private boolean required;
    private boolean unique;
    private Integer minLength;
    private Integer maxLength;
    private boolean id;

    /**
     * Set by the application rather than accepted from a create/update request — so it is left out
     * of the Create DTO and out of {@code applyValues}.
     *
     * <p>Both templates must filter on the same predicate: {@code applyValues} calls
     * {@code request.getX()} for every field the DTO declares, so any disagreement between them is
     * a compile error in the generated project. Holding the decision here rather than repeating a
     * condition in two {@code .ftl} files is what keeps them from diverging.
     *
     * <p>Previously this was a literal name check for {@code currentStock}, {@code stockStatus} and
     * {@code status} — stock-management vocabulary hardcoded into a generator meant to build any
     * project. The replacement keys on what the PCSF actually declares: an attribute explicitly
     * marked as not shown in a form, or the state field of an entity that has a status machine
     * (whose value belongs to the machine's transitions, not to a create request).
     */
    private boolean serverManaged;

    /**
     * A compilable Java expression producing a valid value for this field, used by the generated
     * repository tests (e.g. {@code "abc"}, {@code 1}, {@code java.time.LocalDate.now()}). It
     * respects {@code @Size} bounds so Hibernate's bean validation accepts it on persist.
     *
     * <p>{@code null} when the field's type is not one we can construct — typically an enum or
     * helper class the AI introduced during logic injection. A required field with no sample value
     * makes its entity non-{@link BackendEntity#isTestable() testable}.
     */
    private String sampleValue;
}
