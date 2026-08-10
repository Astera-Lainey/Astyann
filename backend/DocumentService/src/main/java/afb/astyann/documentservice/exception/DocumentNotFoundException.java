package afb.astyann.documentservice.exception;

import afb.astyann.documentservice.domain.DocumentType;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID projectId, UUID documentId) {
        super("Document " + documentId + " not found for project " + projectId);
    }

    /** For lookups by type, where the caller never had a document id to report. */
    public DocumentNotFoundException(UUID projectId, DocumentType type) {
        super("No APPROVED " + type + " document found for project " + projectId);
    }
}
