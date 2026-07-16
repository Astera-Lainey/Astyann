package afb.astyann.documentservice.exception;

import java.util.UUID;

public class DocumentValidationFailedException extends RuntimeException {
    public DocumentValidationFailedException(UUID documentId, String reason) {
        super("Document " + documentId + " failed validation: " + reason);
    }
}
