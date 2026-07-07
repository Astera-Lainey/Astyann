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
public class PcsfNavItem {
    @JsonAlias({"name", "title", "text"})
    private String label;

    @JsonAlias({"path", "route", "url", "link", "href"})
    private String routePath;

    private String icon;

    @JsonAlias({"roles", "allowedRoles", "permissions", "access", "required_roles"})
    @Builder.Default private List<String> visibleToRoles = new ArrayList<>();

    @JsonAlias({"module"})
    private String moduleId;
}
