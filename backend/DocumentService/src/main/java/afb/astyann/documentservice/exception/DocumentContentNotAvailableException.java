package afb.astyann.documentservice.exception;

import java.util.UUID;

/**
 * The document exists but carries no structured content.
 *
 * <p>Deliberately distinct from {@link DocumentNotFoundException}: this means the document was
 * generated before its JSON was persisted, and the fix is to regenerate it — not to go looking for
 * an id that does not exist. A consumer cannot give a useful error without being able to tell those
 * two apart.
 */
public class DocumentContentNotAvailableException extends RuntimeException {

    public DocumentContentNotAvailableException(UUID documentId) {
        super("Document " + documentId + " has no stored structured content. It was generated "
              + "before content was persisted — regenerate it to populate it.");
    }
}
