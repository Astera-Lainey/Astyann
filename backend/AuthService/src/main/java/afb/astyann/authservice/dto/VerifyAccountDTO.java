package afb.astyann.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyAccountDTO {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Verification code is required")
    private String code;
}
