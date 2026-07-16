package afb.astyann.documentservice.exception;

import java.util.UUID;

public class PcsfNotApprovedException extends RuntimeException {
    public PcsfNotApprovedException(UUID projectId) {
        super("Requirements for project " + projectId + " are not approved yet.");
    }
}
