package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendProjectInfo {
    private String appName;
    private String artifactId;
    private String packageName;
    private String packagePath;
    private String databaseName;
    private String databaseUser;
    private int backendPort;
    private long jwtAccessTokenValidityMs;
    private long jwtRefreshTokenValidityMs;
    private String corsAllowedOriginsDev;
    private String versionPrefix;
}
