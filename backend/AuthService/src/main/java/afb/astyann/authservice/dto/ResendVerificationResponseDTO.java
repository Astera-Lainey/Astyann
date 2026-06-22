package afb.astyann.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationResponseDTO {
    private UUID userId;
    private LocalDateTime verificationExpiryDate;
}
