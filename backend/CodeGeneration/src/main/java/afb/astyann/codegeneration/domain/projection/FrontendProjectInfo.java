package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendProjectInfo {
    private String appName;
    private String angularProjectName;
    private String apiBaseUrl;
    private String primaryColour;
    private String secondaryColour;
    private String neutralColour;
    private String textColour;
    private String primaryDark;
    private String primaryLight;
    private String primaryAlpha;
    private String fontFamily;
    /** Default route for post-login redirects — kebab-case path of the first module. */
    private String defaultRoute;
}
