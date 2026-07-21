package afb.astyann.codegeneration.dto;

import afb.astyann.codegeneration.domain.CodeLayer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApproveCodeRequest {
    /** Empty / null → approve every generated layer for this project. */
    private List<CodeLayer> layers;
    private String approvalComment;
}
