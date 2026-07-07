package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfNonFunctionalRequirements {
    @JsonAlias({"concurrent_users", "maxUsers", "users"})
    private FieldValue<Integer> concurrentUsers;

    @JsonAlias({"responseTime", "responseTimeMs", "response_time", "targetResponseTime"})
    private FieldValue<Integer> targetResponseTimeMs;

    @JsonAlias({"dataVolume", "data_volume", "storage", "storageDescription"})
    private FieldValue<String>  dataVolumeDescription;

    @JsonAlias({"availability", "uptime", "sla", "availabilitySla"})
    private FieldValue<String>  availabilityTarget;

    @JsonAlias({"security", "securityLevel", "security_depth"})
    private FieldValue<String>  securityDepth;

    @JsonAlias({"language", "region", "internationalization", "i18n"})
    private FieldValue<String>  locale;
}
