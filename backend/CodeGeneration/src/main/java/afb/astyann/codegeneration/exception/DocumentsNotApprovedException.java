package afb.astyann.codegeneration.exception;

import java.util.UUID;

public class DocumentsNotApprovedException extends RuntimeException {
    public DocumentsNotApprovedException(UUID projectId) {
        super("Documents for project " + projectId + " are not all approved yet.");
    }
}
