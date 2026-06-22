package afb.astyann.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDTO {
    private UUID    userId;
    private String  email;
    @JsonProperty("isVerified")
    private boolean isVerified;
}
