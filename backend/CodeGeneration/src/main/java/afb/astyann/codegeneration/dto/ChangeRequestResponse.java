package afb.astyann.codegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ChangeRequestResponse {
    private UUID changeRequestId;
    private String status;
}
