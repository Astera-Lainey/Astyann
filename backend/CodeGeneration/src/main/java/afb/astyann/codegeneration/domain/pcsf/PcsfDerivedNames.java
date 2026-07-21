package afb.astyann.codegeneration.domain.pcsf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfDerivedNames {
    private String mavenArtifactId;
    private String javaRootPackage;
    private String angularProjectName;
    private String databaseName;
    private String databaseUser;
}
