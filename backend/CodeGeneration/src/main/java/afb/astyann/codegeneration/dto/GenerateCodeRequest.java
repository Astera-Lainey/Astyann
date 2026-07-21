package afb.astyann.codegeneration.dto;

import afb.astyann.codegeneration.domain.CodeLayer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class GenerateCodeRequest {
    /** Empty / null → generate every layer (BACKEND, FRONTEND, INFRASTRUCTURE). */
    private List<CodeLayer> layers;
}
