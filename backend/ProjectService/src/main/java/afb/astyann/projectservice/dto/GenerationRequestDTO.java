package afb.astyann.projectservice.dto;

import afb.astyann.projectservice.domain.GenerationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerationRequestDTO {

    @NotNull(message = "Generation type is required")
    private GenerationType type;
}
