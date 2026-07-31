package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendFormField {
    private String label;
    private String fieldName;
    /** text | password | email | number | checkbox | date | datetime-local | select */
    private String inputType;
    private boolean required;

    /**
     * Pre-rendered Angular validator list, e.g.
     * {@code Validators.required, Validators.maxLength(120)}.
     *
     * <p>Built in Java rather than assembled in the template: Mustache is logic-less, and emitting
     * a correctly-comma-separated list from conditionals is error-prone. Must be interpolated with
     * a triple-mustache — it contains quotes that {@code DefaultMustacheFactory} would HTML-escape.
     */
    private String validatorsExpression;

    /** Whether {@link #validatorsExpression} is non-empty (Mustache cannot test for blankness). */
    private boolean hasValidators;
}
