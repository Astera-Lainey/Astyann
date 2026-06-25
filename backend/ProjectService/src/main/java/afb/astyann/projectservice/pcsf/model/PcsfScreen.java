package afb.astyann.projectservice.pcsf.model;

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
public class PcsfScreen {
    private String name;
    private String type;
    private String entityId;
    private String moduleId;
    private String routePath;
    @Builder.Default private List<String>      requiredRoles = new ArrayList<>();
    @Builder.Default private List<TableColumn> tableColumns  = new ArrayList<>();
    @Builder.Default private List<FormField>   formFields    = new ArrayList<>();

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class TableColumn {
        private String attributeId;
        private String headerLabel;
        private boolean sortable;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class FormField {
        private String attributeId;
        private String label;
        private String controlType;
    }
}
