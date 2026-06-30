package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias({"screenType", "pageType", "screen_type"})
    private String type;

    @JsonAlias({"entity", "entity_id"})
    private String entityId;

    @JsonAlias({"module"})
    private String moduleId;

    @JsonAlias({"path", "route", "url"})
    private String routePath;

    @JsonAlias({"roles", "allowedRoles", "permissions", "access"})
    @Builder.Default private List<String> requiredRoles = new ArrayList<>();

    @JsonAlias({"columns", "fields", "gridColumns"})
    @Builder.Default private List<TableColumn> tableColumns = new ArrayList<>();

    @JsonAlias({"fields", "inputs", "form", "formInputs"})
    @Builder.Default private List<FormField> formFields = new ArrayList<>();

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class TableColumn {
        @JsonAlias({"attribute", "field", "id", "attribute_id"})
        private String attributeId;

        @JsonAlias({"label", "header", "title", "name"})
        private String headerLabel;

        private boolean sortable;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class FormField {
        @JsonAlias({"attribute", "field", "id", "attribute_id"})
        private String attributeId;

        @JsonAlias({"name", "title", "placeholder"})
        private String label;

        @JsonAlias({"type", "inputType", "control", "input_type"})
        private String controlType;
    }
}
