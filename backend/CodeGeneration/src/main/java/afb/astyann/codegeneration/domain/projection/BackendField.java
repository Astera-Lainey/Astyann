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
