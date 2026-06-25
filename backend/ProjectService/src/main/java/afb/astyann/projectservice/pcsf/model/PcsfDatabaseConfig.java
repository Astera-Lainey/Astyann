package afb.astyann.projectservice.pcsf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfDatabaseConfig {
    private String name;
    private String user;
    private String charset   = "utf8mb4";
    private String collation = "utf8mb4_unicode_ci";
}
