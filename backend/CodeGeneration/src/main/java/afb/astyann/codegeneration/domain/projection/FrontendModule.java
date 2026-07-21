package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendModule {
    private String serviceName;
    private String serviceFileName;
    private String componentPrefix;
    private String entityClassName;
    private String entityFileName;
    private String entityInstanceName;
    private String apiPath;
    private boolean hasCreate;
    private boolean hasRead;
    private boolean hasUpdate;
    private boolean hasDelete;
    @Builder.Default private List<FrontendEndpoint> endpoints = new ArrayList<>();
    @Builder.Default private List<FrontendColumn> listColumns = new ArrayList<>();
    @Builder.Default private List<FrontendFormField> formFields = new ArrayList<>();
}
