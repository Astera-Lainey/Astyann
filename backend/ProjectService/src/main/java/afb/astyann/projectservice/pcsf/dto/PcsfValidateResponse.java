package afb.astyann.projectservice.pcsf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfValidateResponse {
    private boolean valid;
    private String pcsfStatus;
    private List<String> errors;
    private List<String> warnings;
}
