package afb.astyann.documentservice.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID projectId, UUID documentId) {
        super("Document " + documentId + " not found for project " + projectId);
    }
}
