package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendField {
    private String name;
    private String tsType;
    private String label;
    private boolean required;
    private boolean unique;

    // Constraints carried through from the PCSF so the generated form validates the same rules the
    // backend enforces with @Size / @Pattern, instead of only checking `required`.
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
}
