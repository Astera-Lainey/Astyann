package afb.astyann.projectservice.pcsf.model;

import lombok.Data;

@Data
public class PcsfInfrastructureConfig {
    private int    backendPort      = 8080;
    private int    frontendPort     = 80;
    private String diagramRenderer  = "kroki";
    private String krokiInternalUrl = "http://kroki:8000";
    private String deploymentTarget = "VPS";
}
