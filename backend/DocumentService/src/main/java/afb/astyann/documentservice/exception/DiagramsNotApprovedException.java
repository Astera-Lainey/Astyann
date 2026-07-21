package afb.astyann.documentservice.exception;

import java.util.UUID;

public class DiagramsNotApprovedException extends RuntimeException {
    public DiagramsNotApprovedException(UUID projectId) {
        super("Diagrams for project " + projectId + " are not all approved yet (or none exist).");
    }
}
