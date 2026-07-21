package afb.astyann.documentservice.exception;

import java.util.UUID;

public class DocumentVersionNotFoundException extends RuntimeException {
    public DocumentVersionNotFoundException(UUID documentId, UUID snapshotId) {
        super("No archived version " + snapshotId + " found for document " + documentId);
    }
}
