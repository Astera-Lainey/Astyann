package afb.astyann.codegeneration.dto;

import afb.astyann.codegeneration.domain.CodeLayer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RegenerateCodeRequest {
    /** Empty / null → regenerate every non-APPROVED layer. */
    private List<CodeLayer> layers;
}
