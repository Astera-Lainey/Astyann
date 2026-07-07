package afb.astyann.requirementservice.domain.pcsf;

import lombok.Data;

@Data
public class PcsfInfrastructureConfig {
    private int    backendPort      = 8080;
    private int    frontendPort     = 80;
    private String diagramRenderer  = "NONE";
    private String krokiInternalUrl;
    private String deploymentTarget = "VPS";
}
