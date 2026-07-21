package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class InfraProjection {
    private String appName;
    private String artifactId;
    private String backendServiceName;
    private String frontendServiceName;
    private String databaseServiceName;
    private String databaseName;
    private String databaseUser;
    private int backendPort;
    private int frontendPort;
    private String deploymentTarget;
}
