package afb.astyann.requirementservice.domain.pcsf;

import lombok.Data;

@Data
public class PcsfApiConfig {
    private String versionPrefix             = "/api/v1";
    private long   jwtAccessTokenValidityMs  = 3_600_000L;
    private long   jwtRefreshTokenValidityMs = 604_800_000L;
    private int    rateLimitPerMinute        = 1000;
    private String corsAllowedOriginsDev     = "http://localhost:4200";
}
