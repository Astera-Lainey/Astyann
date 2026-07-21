package afb.astyann.codegeneration.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class PcsfApiConfig {
    private String versionPrefix             = "/api/v1";
    private long   jwtAccessTokenValidityMs  = 3_600_000L;
    private long   jwtRefreshTokenValidityMs = 604_800_000L;
    private int    rateLimitPerMinute        = 1000;
    private String corsAllowedOriginsDev     = "http://localhost:4200";

    @JsonAlias({"pageSize", "page_size", "defaultPage"})
    private int    defaultPageSize           = 20;

    @JsonAlias({"maxPage", "max_page_size", "maxResults"})
    private int    maxPageSize               = 100;
}
