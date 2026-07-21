package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendNavItem {
    private String label;
    private String path;
    private String icon;
    @Builder.Default private List<String> roles = new ArrayList<>();
}
