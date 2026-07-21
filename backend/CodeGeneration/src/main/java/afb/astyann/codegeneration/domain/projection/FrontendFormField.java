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
}
