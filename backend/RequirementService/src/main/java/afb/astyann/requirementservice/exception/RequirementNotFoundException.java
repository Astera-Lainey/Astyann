package afb.astyann.requirementservice.exception;

import java.util.UUID;

public class RequirementNotFoundException extends RuntimeException {
    public RequirementNotFoundException(UUID projectId) {
        super("No requirement found for projectId: " + projectId);
    }
}
