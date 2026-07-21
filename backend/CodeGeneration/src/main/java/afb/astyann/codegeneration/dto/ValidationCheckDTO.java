package afb.astyann.codegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ValidationCheckDTO {
    private String name;
    /** PASSED | WARNING | FAILED */
    private String status;
    private String message;
}
