package afb.astyann.requirementservice.domain.pcsf;

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
    private String label;
    private String routePath;
    private String icon;
    @Builder.Default private List<String> visibleToRoles = new ArrayList<>();
    private String moduleId;
}
