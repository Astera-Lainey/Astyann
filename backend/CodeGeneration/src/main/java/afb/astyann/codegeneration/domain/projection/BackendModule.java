package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendModule {
    private String controllerName;
    private String serviceName;
    private String serviceImplName;
    private String repositoryName;
    private String requestMapping;
    private String packageName;
    private String entityClassName;
    private String entityInstanceName;
    @Builder.Default private List<BackendEndpoint> endpoints = new ArrayList<>();
}
